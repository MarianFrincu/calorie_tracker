# Calorie Tracker web client — build the SPA, serve it from nginx.

# ---------------- Stage 1: build ----------------
# ECR Public mirrors of Docker Hub: no pull rate limits.
FROM public.ecr.aws/docker/library/node:22-alpine AS build

WORKDIR /app

# Manifests first: `npm ci` stays cached until dependencies change.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY tsconfig*.json vite.config.ts index.html ./
COPY src ./src

# Baked into the bundle - public by design (Cognito ids are not secrets).
ARG VITE_AUTH_MODE="none"
ARG VITE_COGNITO_USER_POOL_ID=""
ARG VITE_COGNITO_CLIENT_ID=""
ENV VITE_AUTH_MODE=$VITE_AUTH_MODE \
    VITE_COGNITO_USER_POOL_ID=$VITE_COGNITO_USER_POOL_ID \
    VITE_COGNITO_CLIENT_ID=$VITE_COGNITO_CLIENT_ID

# A failing type check or test fails the image build.
RUN npm test && npm run build

# ---------------- Stage 2: serve ----------------
# Non-root nginx (uid 101, port 8080).
FROM public.ecr.aws/nginx/nginx-unprivileged:1.27-alpine

# Only these are substituted, so nginx's own $host/$uri survive.
ENV NGINX_ENVSUBST_FILTER="^(API_UPSTREAM|COGNITO_IDP_ORIGIN|DNS_RESOLVER)$"

# Local defaults; 04-app.yaml overrides them on AWS.
ENV API_UPSTREAM="http://api-gateway:8080" \
    COGNITO_IDP_ORIGIN="https://cognito-idp.eu-central-1.amazonaws.com" \
    DNS_RESOLVER="127.0.0.11"

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q --no-verbose --tries=1 --spider http://127.0.0.1:8080/healthz || exit 1
