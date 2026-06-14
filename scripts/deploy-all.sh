#!/usr/bin/env bash
#
# scripts/deploy-all.sh — full cloud deploy, idempotent.
#
# What it does, in order:
#   1. Source scripts/vars.sh + sanity-check everything
#   2. Create the ECS task role if it doesn't exist (with AmazonECSTaskExecutionRolePolicy,
#      SecretsManagerReadWrite, AmazonBedrockFullAccess)
#   3. Deploy 01-foundation.yaml (VPC + ECR repos + SGs + Cloud Map)
#   4. Push the 4 backend Docker images to ECR (defensive ${braces} on every tag)
#   5. Deploy 02-database.yaml (RDS Postgres + Secrets Manager)
#   6. Deploy 03-cognito.yaml (User Pool + groups + clients)
#   7. Deploy 04-app.yaml (ECS Fargate × 4 + ALB)
#   8. Deploy 05-cicd.yaml (CodePipeline + CodeBuild ← GitHub) — only if GH_CONN_ARN
#      is set in .envrc
#   9. Print the ALB URL + Cognito client id for the desktop client
#
# Usage:
#   ./scripts/deploy-all.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

cd "${CFN_DIR}"

# ── 1. Ensure the ECS task role exists ────────────────────────────────────────
step "Ensuring IAM role ${TASK_ROLE_NAME} exists"
if aws iam get-role --role-name "${TASK_ROLE_NAME}" >/dev/null 2>&1; then
  ok "Role ${TASK_ROLE_NAME} already exists"
else
  cat > /tmp/ecs-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Service": "ecs-tasks.amazonaws.com" },
    "Action": "sts:AssumeRole"
  }]
}
EOF
  aws iam create-role --role-name "${TASK_ROLE_NAME}" \
    --assume-role-policy-document file:///tmp/ecs-trust.json >/dev/null
  for p in \
      arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy \
      arn:aws:iam::aws:policy/SecretsManagerReadWrite \
      arn:aws:iam::aws:policy/AmazonBedrockFullAccess; do
    aws iam attach-role-policy --role-name "${TASK_ROLE_NAME}" --policy-arn "${p}"
  done
  ok "Created ${TASK_ROLE_NAME} with 3 policies attached"
fi

# ── 2. Foundation stack ───────────────────────────────────────────────────────
step "Deploying foundation stack (~3 min)"
aws cloudformation deploy \
  --stack-name "${STACK_FOUNDATION}" \
  --template-file 01-foundation.yaml \
  --parameter-overrides ProjectName="${PROJECT}"
ok "Foundation deployed"

# ── 3. Build + push 4 backend images to ECR ───────────────────────────────────
step "Building + pushing 4 backend images to ECR (~10 min)"
aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR}"
for svc in eureka-server api-gateway backend-core ai-service; do
  echo "  ─── ${svc} ───"
  docker build --platform linux/amd64 \
    -t "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}" \
    "${REPO_ROOT}/${svc}"
  docker push "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}"
done
ok "All 4 images pushed"

# ── 4. Database stack ─────────────────────────────────────────────────────────
step "Deploying database stack (~10 min — RDS provisioning is slow)"
aws cloudformation deploy \
  --stack-name "${STACK_DATABASE}" \
  --template-file 02-database.yaml \
  --parameter-overrides ProjectName="${PROJECT}" \
                        DBEngineVersion="${DB_ENGINE_VERSION}"
ok "Database deployed"

# ── 5. Cognito stack ──────────────────────────────────────────────────────────
step "Deploying cognito stack (~1 min)"
aws cloudformation deploy \
  --stack-name "${STACK_COGNITO}" \
  --template-file 03-cognito.yaml \
  --parameter-overrides ProjectName="${PROJECT}"
ok "Cognito deployed"

# ── 6. App stack (assembles app + Bedrock config) ─────────────────────────────
step "Deploying app stack (~5 min)"
APP_PARAMS=(
  ProjectName="${PROJECT}"
  ImageTag="${IMAGE_TAG}"
  AiProvider="${AI_PROVIDER}"
  LabRoleArn="${TASK_ROLE_ARN}"
)
if [ "${AI_PROVIDER}" = "bedrock" ]; then
  APP_PARAMS+=(BedrockModelId="${BEDROCK_MODEL_ID}")
fi
if [ "${AI_PROVIDER}" = "gemini" ]; then
  if [ -z "${GEMINI_API_KEY}" ]; then
    fail "AI_PROVIDER=gemini but GEMINI_API_KEY is empty. Add it to .envrc."
    exit 1
  fi
  APP_PARAMS+=(GeminiApiKey="${GEMINI_API_KEY}" GeminiModel="${GEMINI_MODEL}")
fi
aws cloudformation deploy \
  --stack-name "${STACK_APP}" \
  --template-file 04-app.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "${APP_PARAMS[@]}"
ok "App deployed"

# ── 7. CI/CD stack (optional — only if GH_CONN_ARN is set) ────────────────────
if [ -n "${GH_CONN_ARN:-}" ] && [ -n "${GH_REPO:-}" ]; then
  step "Deploying CI/CD stack (~1 min)"
  aws cloudformation deploy \
    --stack-name "${STACK_CICD}" \
    --template-file 05-cicd.yaml \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides ProjectName="${PROJECT}" \
                          GitHubConnectionArn="${GH_CONN_ARN}" \
                          GitHubFullRepo="${GH_REPO}" \
                          GitHubBranch="${GH_BRANCH}"
  ok "CI/CD deployed"
else
  warn "Skipping CI/CD (GH_CONN_ARN and/or GH_REPO not set in .envrc)"
fi

# ── 8. Wait for ECS tasks to become healthy ───────────────────────────────────
step "Waiting for ECS tasks to come up (~2-3 min)"
for i in $(seq 1 40); do
  HEALTHY=$(aws ecs describe-services --cluster "${PROJECT}-cluster" \
    --services "${PROJECT}-eureka" "${PROJECT}-gateway" "${PROJECT}-core" "${PROJECT}-ai" \
    --query "length(services[?deployments[0].rolloutState=='COMPLETED' && runningCount==\`1\`])" \
    --output text 2>/dev/null || echo 0)
  if [ "${HEALTHY}" = "4" ]; then
    ok "All 4 ECS services healthy"
    break
  fi
  echo "  attempt ${i}: ${HEALTHY}/4 healthy — waiting..."
  sleep 15
done

# ── 9. Print connection info ──────────────────────────────────────────────────
API_URL=$(aws cloudformation describe-stacks --stack-name "${STACK_APP}" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)
APP_CLIENT=$(aws cloudformation describe-stacks --stack-name "${STACK_COGNITO}" \
  --query "Stacks[0].Outputs[?OutputKey=='AppClientId'].OutputValue" --output text)

echo
echo "${BOLD}════════════ Deploy complete ════════════${RESET}"
printf "  %-18s %s\n" "API URL:"        "${API_URL}"
printf "  %-18s %s\n" "Cognito client:" "${APP_CLIENT}"
printf "  %-18s %s\n" "Cognito region:" "${AWS_DEFAULT_REGION}"
echo "${BOLD}═════════════════════════════════════════${RESET}"
echo
echo "Launch the desktop client with:"
echo "  mvn -f desktop-client/pom.xml javafx:run \\"
echo "    -Dapi.base.url=\"${API_URL}\" \\"
echo "    -Dauth.mode=cognito \\"
echo "    -Dcognito.region=\"${AWS_DEFAULT_REGION}\" \\"
echo "    -Dcognito.client.id=\"${APP_CLIENT}\""
echo
echo "Or update desktop-client/src/main/resources/config.properties with those values."
