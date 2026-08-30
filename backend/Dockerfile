# --- Build stage: compiles the jar. Not part of the final image. ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Dependencies first so they're cached across builds when only source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# --- Runtime stage: JRE only, non-root, nothing else from the build stage. ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
