#!/usr/bin/env bash
#
# scripts/redeploy-app.sh — quick re-deploy of just the app stack.
#
# Use when you've edited 04-app.yaml or want to change AI_PROVIDER / BedrockModelId.
# Doesn't touch foundation/database/cognito so it's fast (~5 min).
#
# Usage:
#   ./scripts/redeploy-app.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

cd "${CFN_DIR}"

step "Re-deploying app stack"

APP_PARAMS=(
  ProjectName="${PROJECT}"
  ImageTag="${IMAGE_TAG}"
  AiProvider="${AI_PROVIDER}"
  LabRoleArn="${TASK_ROLE_ARN}"
)
if [ "${AI_PROVIDER}" = "bedrock" ]; then
  APP_PARAMS+=(BedrockModelId="${BEDROCK_MODEL_ID}")
fi

aws cloudformation deploy \
  --stack-name "${STACK_APP}" \
  --template-file 04-app.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides "${APP_PARAMS[@]}"

ok "App stack updated"

step "Waiting for tasks to roll (~2 min)"
for i in $(seq 1 30); do
  HEALTHY=$(aws ecs describe-services --cluster "${PROJECT}-cluster" \
    --services "${PROJECT}-eureka" "${PROJECT}-gateway" "${PROJECT}-core" "${PROJECT}-ai" \
    --query "length(services[?deployments[0].rolloutState=='COMPLETED' && runningCount==\`1\`])" \
    --output text 2>/dev/null || echo 0)
  if [ "${HEALTHY}" = "4" ]; then
    ok "All 4 ECS services healthy"
    break
  fi
  echo "  attempt ${i}: ${HEALTHY}/4 healthy — waiting..."
  sleep 10
done

API_URL=$(aws cloudformation describe-stacks --stack-name "${STACK_APP}" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)
echo
echo "API_URL = ${API_URL}"
