FROM openjdk:17-jdk-alpine AS builder

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradlew ./gradlew
COPY gradle ./gradle

RUN chmod +x ./gradlew
RUN ./gradlew dependencies

COPY src ./src

RUN ./gradlew clean build

FROM openjdk:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
