# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY services services
COPY infra/spring-boot-admin infra/spring-boot-admin
RUN --mount=type=cache,id=gradle-calculation-service,target=/root/.gradle \
    ./gradlew --no-daemon :services:calculation-service:bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/services/calculation-service/build/libs/*.jar /app/app.jar
EXPOSE 8082
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
