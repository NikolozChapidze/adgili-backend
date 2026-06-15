# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r adgili && useradd -r -g adgili adgili

COPY --from=build /workspace/target/adgili-backend.jar app.jar
RUN chown -R adgili:adgili /app

USER adgili

ENV PORT=8080
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

# JVM args hardcoded into the entrypoint (no shell expansion to go wrong).
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
