FROM eclipse-temurin:21-jre
WORKDIR /app
COPY order/build/libs/*SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
