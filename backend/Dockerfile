FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package -DfinalName=app

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

ENV APP_ENV=prod
ENV SPRING_PROFILES_ACTIVE=prod

RUN groupadd -r familyagent && useradd -r -g familyagent familyagent && \
    mkdir -p /var/log/familyagent && chown -R familyagent:familyagent /var/log/familyagent

COPY --from=build --chown=familyagent:familyagent /workspace/target/app.jar /app/app.jar

USER familyagent

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
