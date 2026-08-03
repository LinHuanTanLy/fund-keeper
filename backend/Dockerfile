FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S fundkeeper \
    && adduser -S fundkeeper -G fundkeeper

WORKDIR /app

COPY --from=build \
    /workspace/target/backend-0.0.1-SNAPSHOT.jar \
    /app/fund-keeper.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

USER fundkeeper

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/fund-keeper.jar"]
