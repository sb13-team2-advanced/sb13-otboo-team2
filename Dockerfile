# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY --chmod=755 gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src/main ./src/main

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir uploads \
    && chown app:app uploads

COPY --from=build --chown=app:app /workspace/app.jar ./app.jar

ENV SPRING_PROFILES_ACTIVE=prod

USER app:app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
