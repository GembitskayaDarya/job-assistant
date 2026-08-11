FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring

# Sprint 10 Step 4: application-materials.storage.local.root-directory defaults to
# ./data/application-materials, resolved against this WORKDIR - i.e. /app/data/application-materials.
# /app itself is root-owned (created by WORKDIR above, before USER switches to the non-root spring
# user), so the directory is created and handed to spring here; otherwise LocalFileStorageAdapter's
# first write would fail with a permission error at runtime.
RUN mkdir -p /app/data/application-materials && chown -R spring:spring /app/data

COPY --from=build /workspace/build/libs/*.jar app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
