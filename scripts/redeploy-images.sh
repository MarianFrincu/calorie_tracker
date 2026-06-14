#!/usr/bin/env bash
#
# scripts/redeploy-images.sh — rebuild Docker images locally + push to ECR
# + force ECS to roll new tasks. Skips CFN entirely.
#
# Use when you changed backend code and want to test in cloud WITHOUT pushing to
# git (i.e., without going through the CI/CD pipeline).
#
# Usage:
#   ./scripts/redeploy-images.sh                      # rebuilds all 4 services
#   ./scripts/redeploy-images.sh backend-core         # rebuild only one
#   ./scripts/redeploy-images.sh backend-core ai-service   # subset

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

# Service list: arg list, or all 4
if [ "$#" -gt 0 ]; then
  SERVICES=("$@")
else
  SERVICES=(eureka-server api-gateway backend-core ai-service)
fi

# Validate
for svc in "${SERVICES[@]}"; do
  case "${svc}" in
    eureka-server|api-gateway|backend-core|ai-service) ;;
    *) fail "Unknown service '${svc}'"; exit 1 ;;
  esac
done

step "Authenticating to ECR"
aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR}"

step "Building + pushing: ${SERVICES[*]}"
for svc in "${SERVICES[@]}"; do
  echo "  ─── ${svc} ───"
  docker build --platform linux/amd64 \
    -t "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}" \
    "${REPO_ROOT}/${svc}"
  docker push "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}"
done
ok "Images pushed"

step "Rolling ECS services"
for svc in "${SERVICES[@]}"; do
  case "${svc}" in
    eureka-server) ecs_svc="${PROJECT}-eureka" ;;
    api-gateway)   ecs_svc="${PROJECT}-gateway" ;;
    backend-core)  ecs_svc="${PROJECT}-core" ;;
    ai-service)    ecs_svc="${PROJECT}-ai" ;;
  esac
  echo "  rolling ${ecs_svc}..."
  aws ecs update-service --cluster "${PROJECT}-cluster" \
    --service "${ecs_svc}" --force-new-deployment >/dev/null
done
ok "ECS rollout triggered"

echo
echo "Watch progress with:"
echo "  aws ecs describe-services --cluster ${PROJECT}-cluster \\"
echo "    --services ${PROJECT}-eureka ${PROJECT}-gateway ${PROJECT}-core ${PROJECT}-ai \\"
echo "    --query 'services[].[serviceName,runningCount,deployments[0].rolloutState]' --output table"
