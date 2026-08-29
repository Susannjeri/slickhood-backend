# --- Stage 1: Build the project with Maven ---
# Build and run with Java 21, matching pom.xml.
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn -B clean verify

# --- Stage 2: Create the final production image ---
FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=builder /app/${JAR_FILE} silverocean.jar
ENTRYPOINT ["java", "-jar", "silverocean.jar"]
