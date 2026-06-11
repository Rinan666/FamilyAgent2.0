FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV APP_ENV=prod
ENV SPRING_PROFILES_ACTIVE=prod

RUN mkdir -p /var/log/familyagent

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
