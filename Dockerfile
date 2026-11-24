FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 devknow && mkdir -p /app/storage && chown devknow /app/storage
COPY --from=build /app/target/devknow-backend-1.0.0.jar app.jar
USER devknow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
