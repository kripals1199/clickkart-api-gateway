# Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN addgroup --system clickkart && adduser --system --ingroup clickkart clickkart
WORKDIR /app
COPY --from=build /workspace/target/clickkart-api-gateway.jar app.jar
USER clickkart

ENV SPRING_PROFILES_ACTIVE=dev
EXPOSE 8080

# Derives its scheme from the same TLS_ENABLED that switches the listener, so the probe cannot
# drift out of step with what the server is actually speaking - a healthcheck still curling http
# against an https listener reports the container unhealthy while it serves traffic perfectly.
# -k because the development certificate is self-signed; this is a loopback call inside the
# container, so there is no transport to intercept and nothing for verification to protect here.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD sh -c 'SCHEME=http; [ "$TLS_ENABLED" = "true" ] && SCHEME=https; \
      curl -fsSk "$SCHEME://localhost:${SERVER_PORT:-8080}/actuator/health" | grep -q "\"status\":\"UP\""' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
