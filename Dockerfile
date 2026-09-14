# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Traz os patches de seguranca do Alpine (libcrypto3/libssl3/openssl, libexpat) para o Trivy.
RUN apk upgrade --no-cache
RUN addgroup -S fiapx && adduser -S fiapx -G fiapx
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER fiapx
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
