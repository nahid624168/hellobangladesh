FROM gradle:8.10-jdk17 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar app.jar
RUN useradd --system --no-create-home appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
