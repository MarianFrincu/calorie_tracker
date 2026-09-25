# ---- Build stage ----
# ECR Public mirror of Docker Hub: no pull rate limits.
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Non-root (uid 10001 avoids the base image's 'ubuntu' user).
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
