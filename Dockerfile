# syntax=docker/dockerfile:1.7

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy pom first for layer caching of dependencies
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# Copy sources and build
COPY src src
RUN mvn -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd -r adgili && useradd -r -g adgili adgili

COPY --from=build /workspace/target/adgili-backend.jar app.jar
RUN chown -R adgili:adgili /app

USER adgili

# Railway sets $PORT automatically. Default 8080 for local.
ENV PORT=8080
EXPOSE 8080

# Tune JVM for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
