FROM gradle:8.14.0-jdk17 AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/build/libs/url-shortner-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
