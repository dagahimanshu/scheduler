FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve -q
COPY src src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar scheduler.jar
RUN mkdir -p /var/data/scheduler/oauth-tokens /app/data
EXPOSE 8080
CMD ["java", "-jar", "scheduler.jar", "--spring.profiles.active=prod"]
