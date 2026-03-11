FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy the parent pom and all module poms for dependency resolution
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY routeservice/pom.xml ./routeservice/
COPY schedulerservice/pom.xml ./schedulerservice/

# Copy maven wrapper from the service
COPY routeservice/.mvn/ ./routeservice/.mvn/
COPY routeservice/mvnw ./routeservice/

# Download dependencies (use the service's maven wrapper)
RUN cd routeservice && chmod +x mvnw && ./mvnw -f ../pom.xml dependency:go-offline -pl common,routeservice -am -q || true

# Copy source code
COPY common/src/ ./common/src/
COPY routeservice/src/ ./routeservice/src/

# Build common first, then routeservice
RUN cd routeservice && ./mvnw -f ../pom.xml package -DskipTests -pl common,routeservice -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/routeservice/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8081
