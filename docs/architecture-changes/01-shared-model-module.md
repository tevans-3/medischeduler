# Issue 1: Shared Model Module

## The Problem

Both the **routeservice** and the **schedulerservice** defined their own copies of the `Student`, `Teacher`, `PotentialMatch`, and `Assignment` model classes. These copies lived in separate packages (`dfm.medischeduler_routeservice` and `dfm.medischeduler_schedulerservice`) but represented the exact same domain concepts.

This created several risks:

- **Drift**: If a field was added to `Student` in one service but not the other, the services would silently disagree on the data shape. Kafka messages serialized by one service could fail to deserialize in the other.
- **Duplication**: Bug fixes or new validation rules had to be applied in two places, doubling maintenance effort.
- **Inconsistent serialization**: Each copy could develop different Jackson annotations, default values, or builder implementations, leading to subtle data-corruption bugs.

## The Fix

We created a third Maven module, `backend/common/`, that contains the canonical model classes. Both services now depend on this module via their `pom.xml`:

```xml
<dependency>
    <groupId>dfm</groupId>
    <artifactId>medischeduler-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

The local copies in each service were deleted, and all import statements were updated from:

```java
import dfm.medischeduler_routeservice.Student;
```

to:

```java
import dfm.medischeduler_common.model.Student;
```

### Module Layout

```
backend/
  common/
    pom.xml                          <-- packaging = jar (not spring-boot)
    src/main/java/dfm/medischeduler_common/model/
      Student.java
      Teacher.java
      PotentialMatch.java
      Assignment.java
  routeservice/
    pom.xml                          <-- depends on common
  schedulerservice/
    pom.xml                          <-- depends on common
  pom.xml                            <-- parent POM aggregates all three
```

### Why a Library JAR?

The `common` module uses `<packaging>jar</packaging>` and does **not** include the `spring-boot-maven-plugin`. This is important because Spring Boot's plugin repackages the JAR into a fat executable JAR with a custom class layout. Other Maven modules cannot use a fat JAR as a dependency — they need a plain library JAR.

## Key Concept: Multi-Module Maven Projects

Maven's multi-module (reactor) build lets you split a project into sub-modules that are built together. The parent POM lists its children in a `<modules>` block:

```xml
<modules>
    <module>common</module>
    <module>routeservice</module>
    <module>schedulerservice</module>
</modules>
```

When you run `mvn install` from the parent directory, Maven:

1. Resolves the dependency graph between modules.
2. Builds them in dependency order (`common` first, then the services).
3. Installs each artifact into your local `~/.m2/repository`.

This means `routeservice` and `schedulerservice` can reference `common` as a normal `<dependency>` — Maven resolves it from the reactor build without needing it published to a remote repository.

## When to Extract a Shared Module

A shared module is worth the overhead when:

- **Two or more deployable units** share the same data classes.
- The shared classes are **serialized across a boundary** (Kafka, REST, database) — any mismatch causes runtime failures that are hard to debug.
- The shared classes are **stable enough** that they won't need to diverge between services.

If only one service uses a class, keep it local. Premature extraction creates coupling without benefit.
