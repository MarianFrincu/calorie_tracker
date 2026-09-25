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
# CA bundle to verify RDS's certificate (sslmode=verify-full on AWS). The
# directory is created first: ADD --chmod=644 would make a new directory 644
# too, and the app user couldn't enter it.
RUN mkdir -p /app/certs
ADD --chmod=644 https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /app/certs/rds-global-bundle.pem
COPY --from=build /app/target/*.jar app.jar
# Non-root (uid 10001 avoids the base image's 'ubuntu' user).
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
USER 10001
# Fail the build, not the deployment, if the app user can't read the bundle.
RUN test -r /app/certs/rds-global-bundle.pem
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
