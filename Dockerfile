# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

COPY pom.xml .
COPY coop-common/pom.xml coop-common/
COPY coop-producer/pom.xml coop-producer/
COPY coop-consumer-recepcao/pom.xml coop-consumer-recepcao/
COPY coop-consumer-comunicacao/pom.xml coop-consumer-comunicacao/
COPY coop-consumer-qualidade/pom.xml coop-consumer-qualidade/
COPY coop-consumer-auditoria/pom.xml coop-consumer-auditoria/
COPY coop-consumer-indicadores/pom.xml coop-consumer-indicadores/

COPY coop-common/src coop-common/src
COPY coop-producer/src coop-producer/src
COPY coop-consumer-recepcao/src coop-consumer-recepcao/src
COPY coop-consumer-comunicacao/src coop-consumer-comunicacao/src
COPY coop-consumer-qualidade/src coop-consumer-qualidade/src
COPY coop-consumer-auditoria/src coop-consumer-auditoria/src
COPY coop-consumer-indicadores/src coop-consumer-indicadores/src

RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime-base
WORKDIR /app
RUN addgroup -S coop && adduser -S coop -G coop
USER coop:coop

FROM runtime-base AS producer
COPY --from=builder /workspace/coop-producer/target/coop-producer-1.0.0.jar app.jar
EXPOSE 8180
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS recepcao
COPY --from=builder /workspace/coop-consumer-recepcao/target/coop-consumer-recepcao-1.0.0.jar app.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS comunicacao
COPY --from=builder /workspace/coop-consumer-comunicacao/target/coop-consumer-comunicacao-1.0.0.jar app.jar
EXPOSE 8182
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS qualidade
COPY --from=builder /workspace/coop-consumer-qualidade/target/coop-consumer-qualidade-1.0.0.jar app.jar
EXPOSE 8183
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS auditoria
COPY --from=builder /workspace/coop-consumer-auditoria/target/coop-consumer-auditoria-1.0.0.jar app.jar
EXPOSE 8184
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS indicadores
COPY --from=builder /workspace/coop-consumer-indicadores/target/coop-consumer-indicadores-1.0.0.jar app.jar
EXPOSE 8185
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
