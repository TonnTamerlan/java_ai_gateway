# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY services services
COPY infra/spring-boot-admin infra/spring-boot-admin
COPY infra/eureka-server infra/eureka-server
RUN --mount=type=cache,id=gradle-eureka-server,target=/root/.gradle \
    ./gradlew --no-daemon :infra:eureka-server:bootJar

FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/infra/eureka-server/build/libs/*.jar /app/app.jar
EXPOSE 8761
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
