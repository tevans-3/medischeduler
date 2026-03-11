FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy the parent pom and all module poms for dependency resolution
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY schedulerservice/pom.xml ./schedulerservice/
COPY routeservice/pom.xml ./routeservice/

# Copy maven wrapper from the service
COPY schedulerservice/.mvn/ ./schedulerservice/.mvn/
COPY schedulerservice/mvnw ./schedulerservice/

# Download dependencies (use the service's maven wrapper)
RUN cd schedulerservice && chmod +x mvnw && ./mvnw -f ../pom.xml dependency:go-offline -pl common,schedulerservice -am -q || true

# Copy source code
COPY common/src/ ./common/src/
COPY schedulerservice/src/ ./schedulerservice/src/

# Build common first, then schedulerservice
RUN cd schedulerservice && ./mvnw -f ../pom.xml package -DskipTests -pl common,schedulerservice -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/schedulerservice/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8082
