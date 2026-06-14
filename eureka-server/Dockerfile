# ---- Build stage: compile + package inside a JDK 21 container ----
# This makes the build independent of the host JDK (your host has Java 26).
# ECR Public mirror of Docker Hub — avoids unauthenticated rate limits in CodeBuild.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy pom first so Maven dependency download is cached unless the pom changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: small JRE-only image ----
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
# curl is used by the docker-compose healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
