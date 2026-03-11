# Issue 7: Credential Externalization

## The Problem

The original `application.properties` files contained hardcoded credentials:

```properties
# DO NOT do this
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=myRedisPassword
routes-api.api-key=AIzaSy...actual_key_here
```

This is a security risk for several reasons:

- **Version control exposure**: Properties files are committed to Git. Anyone with repo access (including public repos) can read the credentials.
- **Environment coupling**: The same credentials are used in development, staging, and production. Changing a password requires a code change and redeployment.
- **Secret rotation**: If a key is compromised, you must commit a new value, build, and deploy — instead of just updating an environment variable.

## The Fix

### Spring Property Placeholders

All sensitive values now use Spring's `${VAR:default}` placeholder syntax:

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.username=${REDIS_USERNAME:default}
spring.data.redis.password=${REDIS_PASSWORD:}
routes-api.api-key=${ROUTES_API_KEY:}
```

The syntax `${REDIS_HOST:localhost}` means:
- Use the value of the `REDIS_HOST` environment variable if it exists.
- Otherwise, fall back to `localhost`.

Empty defaults (`:}`) mean the property is blank unless explicitly set — appropriate for passwords and API keys that should never have a default.

### Docker Compose Environment Block

The `docker-compose.yml` passes environment variables into each container:

```yaml
routeservice:
  environment:
    - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    - REDIS_HOST=${REDIS_HOST:-redis}
    - REDIS_PORT=${REDIS_PORT:-6379}
    - REDIS_USERNAME=${REDIS_USERNAME:-default}
    - REDIS_PASSWORD=${REDIS_PASSWORD:-}
    - ROUTES_API_KEY=${ROUTES_API_KEY:-}
```

The `${VAR:-default}` syntax here is Docker Compose's shell-style variable expansion (note the dash). It reads from the host's environment or a `.env` file in the project root.

### .env.example Template

A `.env.example` file documents all required variables without containing actual secrets:

```
# Copy this file to .env and fill in your values.
# NEVER commit the .env file to version control.

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=
ROUTES_API_KEY=
VITE_GOOGLE_MAPS_API_KEY=
```

The `.env` file (with real values) is listed in `.gitignore`.

## Key Concept: The Twelve-Factor App — Config

This change implements [Factor III: Config](https://12factor.net/config) from the Twelve-Factor App methodology:

> Store config in the environment.

"Config" here means anything that varies between deploys (staging, production, developer workstations) — database URLs, API keys, feature flags. Code does not vary between deploys; config does. Therefore, config should not be in the code.

### Environment Variable Hierarchy in Spring Boot

Spring Boot resolves properties from multiple sources, in this priority order (highest first):

1. Command-line arguments (`--spring.data.redis.host=...`)
2. Java system properties (`-Dspring.data.redis.host=...`)
3. OS environment variables (`SPRING_DATA_REDIS_HOST=...`)
4. `application.properties` / `application.yml`
5. `@PropertySource` annotations

Environment variables override properties files, which means the defaults in `application.properties` are just that — defaults. Production deployments set the real values via environment variables, Kubernetes secrets, or a secrets manager.

### Spring Boot's Relaxed Binding

Spring Boot maps environment variables to properties using "relaxed binding":

| Property | Environment Variable |
|----------|---------------------|
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` |
| `routes-api.api-key` | `ROUTES_API_API_KEY` |

Dots become underscores, hyphens become underscores, and everything is uppercased. You can use either form.

In this project, we use explicit `${VAR}` placeholders rather than relying on relaxed binding, because it makes the mapping visible and unambiguous.

## What NOT to Put in Environment Variables

Environment variables are appropriate for:
- Connection strings (hosts, ports)
- API keys and passwords
- Feature flags that vary by environment

They are NOT appropriate for:
- Large configuration objects (use config files or a config server)
- Binary data (certificates — use file mounts or a secrets manager)
- Values that change at runtime (use a feature flag service)

For this project, all secrets are simple strings, so environment variables are the right choice.
