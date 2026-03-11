# Issue 4: Non-Blocking Rate Limiting

## The Problem

The `BatchManager` class manages requests to the Google Routes API, which enforces a rate limit of 2,500 matrix elements per minute. When the limit was exceeded, the original code used a busy-wait loop:

```java
private void pollAndWait() {
    Instant startTime = Instant.parse(
        redisApi.getGlobalStateMetadata("processingStartTime"));
    Duration elapsed = Duration.between(startTime, Instant.now());

    while (elapsed.getSeconds() < 60) {
        Thread.sleep(1000);       // Block the thread for 1 second
        elapsed = Duration.between(startTime, Instant.now());
    }
}
```

This was called recursively from `processBatch`:

```java
private void processBatch(RequestBatch batch) {
    if (elemProcessed <= MAX_ELEMENTS_PER_MINUTE) {
        // ... send batch ...
    } else {
        pollAndWait();            // Block for up to 60 seconds
        processBatch(batch);      // Recursive retry
    }
}
```

### Why This Is Bad

1. **Thread starvation**: The thread sits in `Thread.sleep()` doing nothing for up to 60 seconds. In a server with a limited thread pool (Spring Boot defaults to 200 Tomcat threads), blocked threads reduce the server's capacity to handle other requests.

2. **Unbounded recursion**: If the rate limit keeps being hit, `processBatch` calls itself recursively. While unlikely to cause a stack overflow in practice (each call waits ~60 seconds), recursive retry patterns are fragile and hard to reason about.

3. **Wasted CPU cycles**: Even though `Thread.sleep()` doesn't burn CPU, the thread is still allocated and tracked by the JVM. A single-threaded scheduled executor is more resource-efficient.

## The Fix

### ScheduledExecutorService

The busy-wait loop was replaced with a `ScheduledExecutorService`:

```java
private final ScheduledExecutorService scheduler =
    Executors.newSingleThreadScheduledExecutor();
```

When the rate limit is exceeded, instead of blocking, the batch is scheduled for a delayed retry:

```java
private void processBatch(RequestBatch batch) {
    if (elemProcessed <= MAX_ELEMENTS_PER_MINUTE) {
        // ... send batch ...
    } else {
        long delaySeconds = calculateRemainingWaitSeconds();
        scheduler.schedule(
            () -> processBatch(batch),
            delaySeconds,
            TimeUnit.SECONDS);
    }
}
```

The `calculateRemainingWaitSeconds()` method computes exactly how long to wait:

```java
private long calculateRemainingWaitSeconds() {
    String startTimeStr = redisApi.getGlobalStateMetadata("processingStartTime");
    if (startTimeStr == null) return 60;

    Instant startTime = Instant.parse(startTimeStr);
    long elapsed = Duration.between(startTime, Instant.now()).getSeconds();
    return Math.max(0, 60 - elapsed);
}
```

A `@PreDestroy` method ensures clean shutdown:

```java
@PreDestroy
public void shutdown() {
    scheduler.shutdownNow();
}
```

## Key Concept: Blocking vs. Non-Blocking Waiting

### Blocking (Thread.sleep)

```
Thread-1:  [process] [sleep 1s] [sleep 1s] ... [sleep 1s] [process]
                      |<--------- 60 seconds --------->|
                      Thread is allocated but doing nothing
```

The thread cannot be reused for any other work during the wait.

### Non-Blocking (ScheduledExecutorService)

```
Thread-1:  [process] [schedule retry] [free for other work]
                          |
                          v
Scheduler: .............. [delayed 45s] ...... [process]
```

The calling thread returns immediately. The retry runs later on the executor's thread, which is a single dedicated thread — much cheaper than blocking a Tomcat request thread.

### When to Use Each

| Pattern | Use When |
|---------|----------|
| `Thread.sleep()` | Simple scripts, tests, or when the thread has nothing else to do |
| `ScheduledExecutorService` | Server-side code where threads are shared resources |
| `CompletableFuture.delayedExecutor()` | When you need to chain async operations |
| Reactive/Coroutine delay | In reactive (WebFlux) or Kotlin coroutine contexts |

## Java Concurrency: ScheduledExecutorService

`ScheduledExecutorService` is part of `java.util.concurrent` (since Java 5). Key points:

- `Executors.newSingleThreadScheduledExecutor()` creates an executor with one thread. Tasks are queued and executed sequentially, which is exactly what we want for rate-limited retries — we don't want multiple retries running concurrently.

- `schedule(Runnable, delay, TimeUnit)` runs the task once after the delay. Unlike `scheduleAtFixedRate()`, it doesn't repeat.

- The executor must be shut down when the application stops. Spring's `@PreDestroy` annotation marks a method to be called during bean destruction. Without this, the executor thread keeps the JVM alive after the application context closes.

- `shutdownNow()` (vs. `shutdown()`) is appropriate here because pending retries are not critical — if the app is stopping, there's no point waiting for a rate-limit window to open.
