ARG MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17
ARG JAVA_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

COPY pom.xml ./
COPY settings-docker.xml /root/.m2/settings.xml
RUN mvn -B dependency:go-offline -s /root/.m2/settings.xml

COPY src ./src
RUN mvn -B -DskipTests package -DfinalName=app -s /root/.m2/settings.xml

FROM ${JAVA_RUNTIME_IMAGE} AS runtime

WORKDIR /app

ENV APP_ENV=prod
ENV SPRING_PROFILES_ACTIVE=prod

RUN groupadd -r familyagent && useradd -r -g familyagent familyagent && \
    mkdir -p /var/log/familyagent && chown -R familyagent:familyagent /var/log/familyagent

COPY --from=build --chown=familyagent:familyagent /workspace/target/app.jar /app/app.jar

USER familyagent

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar", "--spring.profiles.active=prod"]
