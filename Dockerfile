# Multi-stage build: compiles with the Maven Wrapper against JDK 21, then runs on a slim JRE.
# Docker is NOT required to run this project locally - see README.md for running directly with
# `mvnw spring-boot:run`. This file exists only for deployment environments that require a
# container image.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Runs as a non-root user - standard container hardening, not specific to this project.
RUN useradd --system --create-home --shell /usr/sbin/nologin railpredictor
COPY --from=build /app/target/rail-predictor-*.jar app.jar
USER railpredictor

# No secret is ever baked into the image - every credential (DB_PASSWORD, RAILRADAR_API_KEY, ...)
# must be supplied at container run time as an environment variable. See .env.example for the
# full list.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
