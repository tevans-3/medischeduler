# Issue 2: Decouple the OR-Tools Solver from Redis

## The Problem

The `ORToolsScheduler` class mixed two responsibilities:

1. **CPU-bound optimization** — building the constraint model and running the CP-SAT solver.
2. **I/O operations** — reading students, teachers, and route data from Redis; writing index maps and results back to Redis; sending a Kafka notification.

During the solve phase, the scheduler made individual Redis calls inside nested loops:

```java
// Inside populateAssignedMatrix — called numStudents x numTeachers times
redisApi.setIndexMapStudentToSolverResult(i, student.getId(), clientId);
redisApi.setIndexMapTeacherToSolverResult(j, teacher.getId(), clientId);

// Inside extractAssignments — called for every assigned pair
String studentId = redisApi.getStudentFromIndex(i, clientId);
String teacherId = redisApi.getTeacherFromIndex(j, clientId);
redisApi.setOptimalAssignment(clientId, studentId, teacherId);
```

Each Redis call involves a network round-trip (even on localhost, ~0.1-0.5 ms). With 50 students and 100 teachers, `populateAssignedMatrix` alone made 5,000+ Redis calls. This added unnecessary latency to an operation that should be purely computational.

The solver also held `@Autowired` references to `RedisAPI` and `KafkaTemplate`, coupling it to infrastructure that has nothing to do with constraint solving.

## The Fix

### Step 1: Pass Data In, Return Results Out

The solver's method signature changed from:

```java
public int runScheduler(String clientId)
```

to:

```java
public Map<String, String> runScheduler(
    String clientId,
    List<Student> students,
    List<Teacher> teachers,
    Map<String, Long> routeMatrixData)
```

All data is now loaded **once** by the caller (`KafkaStream`) before invoking the solver. The solver returns a `Map<String, String>` (studentId to teacherId) instead of writing to Redis directly.

### Step 2: Use Local Maps Instead of Redis

The index maps that track which solver row/column corresponds to which student/teacher ID are now simple `HashMap` instances local to the `runScheduler` method:

```java
Map<Integer, String> studentIndexMap = new HashMap<>();
Map<Integer, String> teacherIndexMap = new HashMap<>();
```

These are passed into `populateAssignedMatrix` and `extractAssignments` instead of calling Redis.

### Step 3: Move Persistence to the Caller

`KafkaStream.handleMessage()` now owns the full lifecycle:

```java
// 1. Load data from Redis (one-time bulk reads)
List<Student> students = redisApi.getAllStudents(clientId);
List<Teacher> teachers = redisApi.getAllTeachers(clientId);
Map<String, Long> routeMatrixData = redisApi.getRouteMatrixData(clientId);

// 2. Run the solver (pure computation, no I/O)
Map<String, String> assignments = orToolsScheduler.runScheduler(
    clientId, students, teachers, routeMatrixData);

// 3. Write results to Redis (batch)
for (Map.Entry<String, String> entry : assignments.entrySet()) {
    redisApi.setOptimalAssignment(clientId, entry.getKey(), entry.getValue());
}

// 4. Notify downstream
kafkaTemplate.send("OPTIMAL_ASSIGNMENTS_TOPIC", clientId, "assignments generated");
```

## Key Concept: Separating Computation from I/O

This refactoring follows the **Functional Core, Imperative Shell** pattern:

- **Functional core**: The solver is a pure function — given inputs, it produces outputs. It has no side effects, no I/O, no framework dependencies. This makes it easy to test (just pass in test data) and easy to reason about (no hidden state mutations).

- **Imperative shell**: The caller (`KafkaStream`) handles all the messy real-world concerns — loading data from Redis, writing results back, sending Kafka notifications. This is the "glue" code that wires the pure computation into the infrastructure.

### Benefits

| Before | After |
|--------|-------|
| Solver requires a running Redis instance to test | Solver can be unit-tested with plain Java objects |
| 5000+ Redis round-trips during solve | 3 bulk reads + N writes (where N = number of assignments) |
| Solver coupled to Spring, Redis, Kafka | Solver is a plain `@Component` with no infrastructure imports |
| Hard to reuse solver logic in a different context | Solver is portable — just pass in data |

### When NOT to Apply This

If the I/O is inherently part of the computation (e.g., a streaming algorithm that must read data lazily because it doesn't fit in memory), then pre-loading everything doesn't make sense. In this case, the data sets are small enough (hundreds of students/teachers) that loading everything into memory first is trivially cheap.
