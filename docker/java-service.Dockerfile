FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY . .

ARG MODULE_NAME
ARG GRADLE_MAX_WORKERS=1
ARG GRADLE_JVM_ARGS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8"

RUN chmod +x gradlew
RUN ./gradlew ":${MODULE_NAME}:bootJar" \
    --no-daemon \
    --max-workers=${GRADLE_MAX_WORKERS} \
    -Dorg.gradle.jvmargs="${GRADLE_JVM_ARGS}"

RUN JAR_PATH="$(ls ${MODULE_NAME}/build/libs/*.jar | grep -v 'plain' | head -n 1)" \
    && cp "${JAR_PATH}" /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8000 8104 8761

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
