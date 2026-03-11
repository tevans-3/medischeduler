# Issue 6: Fix @Async Lock Lifecycle

## The Problem

The `MatchController` had two interrelated bugs:

### Bug 1: @Async Self-Invocation Does Nothing

```java
@RestController
public class MatchController {

    @PostMapping
    public ResponseEntity<String> uploadMatches(...) {
        // ...
        generateMatches(students, teachers, clientId);  // self-call
        // ...
    }

    @Async
    public void generateMatches(...) {  // @Async is ignored!
        // ...
    }
}
```

Spring's `@Async` works through AOP proxies. When Spring creates the `MatchController` bean, it wraps it in a proxy that intercepts method calls. When an external caller invokes `matchController.generateMatches()`, the proxy intercepts the call and runs it on a separate thread.

But when `uploadMatches()` calls `generateMatches()` on `this`, it bypasses the proxy entirely — it's a direct Java method call within the same object. The `@Async` annotation has no effect, and the method runs synchronously on the request thread.

This is a well-known Spring AOP limitation that affects `@Async`, `@Transactional`, `@Cacheable`, and all other proxy-based annotations.

### Bug 2: Lock Released Before Work Completes

```java
try {
    if (lock) {
        try {
            handleStudentsUpload(students, clientId);
            handleTeachersUpload(teachers, clientId);
            generateMatches(students, teachers, clientId);  // if this were async...
        } finally {
            redisLock.releaseLock(clientId);  // ...lock is released immediately!
        }
    }
}
```

If `@Async` had worked, `generateMatches` would return immediately (before matches were actually generated), and the `finally` block would release the lock while match generation was still in progress. Another user could then start a new upload, corrupting the first user's data.

In the current code, since `@Async` doesn't work, the lock is held correctly (by accident) — but the `@Async` annotation is misleading.

## The Fix

### Step 1: Extract to a Separate Service

The async method was moved to a new `MatchGeneratorService` class:

```java
@Service
public class MatchGeneratorService {

    @Async
    public CompletableFuture<Void> generateMatches(
            List<Student> students, List<Teacher> teachers, String clientId) {
        // ... publish matches to Kafka ...
        return CompletableFuture.completedFuture(null);
    }
}
```

Since `MatchGeneratorService` is a separate Spring bean, calls to it from `MatchController` go through the proxy, and `@Async` works correctly.

The return type changed from `void` to `CompletableFuture<Void>`. This gives the caller a handle to track completion.

### Step 2: Release Lock on Completion

```java
@PostMapping
public ResponseEntity<String> uploadMatches(...) {
    boolean lock = redisLock.acquireLock(clientId, 5000L, 60000L);
    if (!lock) {
        return ResponseEntity.status(429).body("Upload in progress.");
    }

    try {
        handleStudentsUpload(students, clientId);
        handleTeachersUpload(teachers, clientId);
        setUploadTotalInRedis(students, teachers, clientId);

        matchGeneratorService.generateMatches(students, teachers, clientId)
            .whenComplete((result, ex) -> {
                redisLock.releaseLock(clientId);
                if (ex != null) {
                    log.error("Match generation failed: {}", ex.getMessage());
                }
            });
    } catch (Exception e) {
        redisLock.releaseLock(clientId);  // release on sync failure
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }

    return ResponseEntity.ok("Uploaded " + students.size() + " students...");
}
```

The lock is now released in the `whenComplete` callback, which fires after the async work finishes (or fails). The HTTP response is returned immediately, but the lock stays held until match generation is done.

## Key Concept: Spring AOP Proxy Mechanics

### How Proxies Work

```
External caller  --->  [Proxy]  --->  [Actual Bean]
                       ^
                       |
                  Intercepts calls,
                  applies @Async,
                  @Transactional, etc.
```

When Spring creates a bean with proxy-based annotations, it generates a wrapper (proxy) around the actual object. The proxy is what gets injected into other beans.

### The Self-Invocation Problem

```
[MatchController Proxy]
    |
    v
[MatchController Actual]
    uploadMatches() {
        this.generateMatches()   <-- "this" is the actual object, not the proxy
    }
```

`this` always refers to the real object, not the proxy. There is no way for the proxy to intercept internal method calls because Java doesn't support it.

### Solutions

| Approach | Complexity | Trade-off |
|----------|-----------|-----------|
| **Extract to separate class** (our choice) | Low | Clean separation of concerns; slightly more classes |
| Self-inject the proxy | Medium | `@Autowired private MatchController self;` then `self.generateMatches()` — works but feels hacky |
| Use `AopContext.currentProxy()` | Medium | Requires `exposeProxy=true`; tightly couples code to Spring AOP internals |
| Switch to AspectJ weaving | High | Byte-code weaving intercepts self-calls, but adds build complexity |

Extracting to a separate service is the simplest and most maintainable solution. It also improves testability — you can unit-test `MatchGeneratorService` independently.

## Key Concept: CompletableFuture for Async Coordination

`CompletableFuture<Void>` is the standard Java way to represent an async operation that produces no result but whose completion you need to track.

- `.whenComplete((result, exception) -> ...)` runs a callback when the future completes, whether successfully or with an error. This is where we release the lock.
- The callback runs on whatever thread completed the future (typically the `@Async` thread pool thread), not the original request thread.
- If the async method throws an exception, `ex` in the callback is non-null, so we can log it while still releasing the lock.
