FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY . .

ARG MODULE_NAME

RUN chmod +x gradlew
RUN ./gradlew ":${MODULE_NAME}:bootJar" --no-daemon

RUN JAR_PATH="$(ls ${MODULE_NAME}/build/libs/*.jar | grep -v 'plain' | head -n 1)" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8000 8104 8761

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
