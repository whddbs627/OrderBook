# syntax=docker/dockerfile:1.7

FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

ENV GRADLE_OPTS="-Dorg.gradle.native=false"

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY gradle.properties gradle.properties
COPY libs libs
COPY services services
COPY load-test load-test

RUN chmod +x gradlew
RUN ./gradlew --no-daemon \
    :services:gateway:bootJar \
    :services:order-service:bootJar \
    :services:risk-service:bootJar \
    :services:matching-engine:bootJar \
    :services:ledger-service:bootJar \
    :services:query-service:bootJar \
    :services:market-data-service:bootJar

FROM eclipse-temurin:21-jre AS runtime

ARG SERVICE_PATH

WORKDIR /app

COPY --from=builder /workspace/${SERVICE_PATH}/build/libs/ /app/

RUN set -eux; \
    useradd --system --create-home orderbook; \
    JAR="$(find /app -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$JAR"; \
    mv "$JAR" /app/app.jar; \
    find /app -maxdepth 1 -type f -name '*.jar' ! -name 'app.jar' -delete; \
    chown -R orderbook:orderbook /app

USER orderbook

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
