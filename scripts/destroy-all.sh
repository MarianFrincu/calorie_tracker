#!/usr/bin/env bash
#
# scripts/destroy-all.sh — full teardown, in safe order.
#
# Deletes in reverse dependency order:
#   1. App stack (kills Fargate + ALB — biggest savings)
#   2. CI/CD stack (if deployed) — empties the artifact bucket first
#   3. Cognito + database stacks
#   4. ECR — empties EVERY image (tagged + untagged), then
#   5. Foundation stack
#
# What it leaves alone (cheap or shared):
#   - CalorieTrackerTaskRole (IAM)
#   - GitHub CodeStar Connection
#   - Bedrock model access grants
#
# Usage:
#   ./scripts/destroy-all.sh           # prompts for confirmation
#   ./scripts/destroy-all.sh --yes     # skips the prompt (use with care)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

SKIP_PROMPT=0
if [ "${1:-}" = "--yes" ] || [ "${1:-}" = "-y" ]; then
  SKIP_PROMPT=1
fi

# ── Confirmation ──────────────────────────────────────────────────────────────
if [ "${SKIP_PROMPT}" = "0" ]; then
  warn "About to DELETE every stack starting with '${PROJECT}-' in account ${ACCOUNT_ID} (${AWS_DEFAULT_REGION})."
  echo "    Stacks to delete:"
  echo "      • ${STACK_APP}"
  echo "      • ${STACK_CICD} (if it exists)"
  echo "      • ${STACK_COGNITO}"
  echo "      • ${STACK_DATABASE}"
  echo "      • ${STACK_FOUNDATION}"
  echo "    All ECR images for ${PROJECT}-* will also be wiped."
  echo
  read -p "Type 'destroy' to continue: " -r CONFIRM
  if [ "${CONFIRM}" != "destroy" ]; then
    fail "Aborted."
    exit 1
  fi
fi

# Helper: delete a stack only if it exists, then wait.
delete_stack() {
  local name="$1"
  if aws cloudformation describe-stacks --stack-name "${name}" >/dev/null 2>&1; then
    echo "  deleting ${name}..."
    aws cloudformation delete-stack --stack-name "${name}"
    if aws cloudformation wait stack-delete-complete --stack-name "${name}" 2>/dev/null; then
      ok "${name} deleted"
    else
      warn "${name} delete didn't complete cleanly — check the console for stuck resources"
    fi
  else
    ok "${name} doesn't exist (already deleted)"
  fi
}

# ── 1. App stack ──────────────────────────────────────────────────────────────
step "Deleting app stack (kills Fargate + ALB)"
delete_stack "${STACK_APP}"

# ── 2. CI/CD stack (empty artifact bucket first) ──────────────────────────────
step "Deleting CI/CD stack (if deployed)"
if aws cloudformation describe-stacks --stack-name "${STACK_CICD}" >/dev/null 2>&1; then
  ART_BUCKET="$(aws cloudformation describe-stack-resources --stack-name "${STACK_CICD}" \
    --logical-resource-id ArtifactBucket \
    --query 'StackResources[0].PhysicalResourceId' --output text 2>/dev/null)"
  if [ -n "${ART_BUCKET}" ] && [ "${ART_BUCKET}" != "None" ]; then
    echo "  emptying artifact bucket s3://${ART_BUCKET}..."
    aws s3 rm "s3://${ART_BUCKET}" --recursive >/dev/null 2>&1 || true
  fi
  delete_stack "${STACK_CICD}"
else
  ok "${STACK_CICD} doesn't exist (already deleted)"
fi

# ── 3. Cognito + database in parallel-ish ─────────────────────────────────────
step "Deleting cognito stack"
delete_stack "${STACK_COGNITO}"

step "Deleting database stack (~5 min — RDS teardown is slow)"
delete_stack "${STACK_DATABASE}"

# ── 4. Empty ECR repos ────────────────────────────────────────────────────────
# Every push leaves untagged image manifests behind. `imageTag=latest` only
# matches the currently-tagged one — these untagged ones block foundation
# delete. Listing by digest and batch-deleting catches all of them.
empty_ecr_repos() {
  for svc in eureka-server api-gateway backend-core ai-service; do
    REPO="${PROJECT}-${svc}"
    if aws ecr describe-repositories --repository-names "${REPO}" >/dev/null 2>&1; then
      IMAGES=$(aws ecr list-images --repository-name "${REPO}" \
        --query 'imageIds[*]' --output json 2>/dev/null)
      COUNT=$(echo "${IMAGES}" | jq 'length' 2>/dev/null || echo 0)
      if [ "${COUNT}" -gt 0 ]; then
        echo "  ${REPO}: ${COUNT} images → deleting"
        aws ecr batch-delete-image --repository-name "${REPO}" \
          --image-ids "${IMAGES}" >/dev/null 2>&1 || true
      else
        echo "  ${REPO}: already empty"
      fi
    fi
  done
}
step "Emptying ECR repos (every image, tagged AND untagged)"
empty_ecr_repos
ok "ECR cleared"

# ── 4b. Cloud Map cleanup ─────────────────────────────────────────────────────
# Foundation owns the Cloud Map private DNS namespace. If the ECS Cloud Map
# service still has registered instances (Eureka task that died but didn't
# de-register), the namespace delete blocks. Force-deregister any leftovers.
step "Cleaning Cloud Map service instances"
NS_ID="$(aws servicediscovery list-namespaces \
  --query "Namespaces[?Name==\`${PROJECT}.local\`].Id" --output text 2>/dev/null || true)"
if [ -n "${NS_ID}" ] && [ "${NS_ID}" != "None" ]; then
  for SVC_ID in $(aws servicediscovery list-services \
        --filters "Name=NAMESPACE_ID,Values=${NS_ID}" \
        --query 'Services[].Id' --output text 2>/dev/null); do
    for INST_ID in $(aws servicediscovery list-instances \
          --service-id "${SVC_ID}" --query 'Instances[].Id' --output text 2>/dev/null); do
      echo "  de-registering ${SVC_ID}/${INST_ID}"
      aws servicediscovery deregister-instance \
        --service-id "${SVC_ID}" --instance-id "${INST_ID}" >/dev/null 2>&1 || true
    done
  done
  ok "Cloud Map instances cleaned"
else
  ok "No Cloud Map namespace to clean"
fi

# ── 4c. Detach + delete orphan ENIs in the VPC ────────────────────────────────
# Fargate task ENIs sometimes take 5-15 min to detach naturally after the
# task stops. We can't delete the VPC's subnets/SGs while ENIs reference them,
# so foundation delete fails. Forcing it here saves the retry round-trip.
step "Cleaning orphan ENIs in the project VPC (if any)"
VPC_ID="$(aws cloudformation describe-stack-resources --stack-name "${STACK_FOUNDATION}" \
  --logical-resource-id Vpc \
  --query 'StackResources[0].PhysicalResourceId' --output text 2>/dev/null || true)"
if [ -n "${VPC_ID}" ] && [ "${VPC_ID}" != "None" ]; then
  for ENI_ID in $(aws ec2 describe-network-interfaces \
        --filters "Name=vpc-id,Values=${VPC_ID}" "Name=status,Values=available" \
        --query 'NetworkInterfaces[].NetworkInterfaceId' --output text 2>/dev/null); do
    echo "  deleting detached ENI ${ENI_ID}"
    aws ec2 delete-network-interface --network-interface-id "${ENI_ID}" >/dev/null 2>&1 || true
  done
  ok "ENIs cleaned"
else
  ok "Foundation stack not found — no VPC to clean"
fi

# ── 5. Foundation stack ───────────────────────────────────────────────────────
step "Deleting foundation stack (VPC, ECR repos, SGs, Cloud Map)"

# Retry pattern: foundation can fail once because of ENIs / Cloud Map lag.
# A second attempt 30 s later usually wins.
attempt=1
while [ "${attempt}" -le 3 ]; do
  if delete_stack "${STACK_FOUNDATION}"; then
    if ! aws cloudformation describe-stacks --stack-name "${STACK_FOUNDATION}" >/dev/null 2>&1; then
      break
    fi
    STATUS="$(aws cloudformation describe-stacks --stack-name "${STACK_FOUNDATION}" \
      --query 'Stacks[0].StackStatus' --output text)"
    if [ "${STATUS}" = "DELETE_FAILED" ]; then
      warn "Foundation delete attempt ${attempt} failed. Diagnostic:"
      aws cloudformation describe-stack-events --stack-name "${STACK_FOUNDATION}" \
        --query 'StackEvents[?ResourceStatus==`DELETE_FAILED`].[LogicalResourceId,ResourceStatusReason]' \
        --output table | head -25
      empty_ecr_repos
      sleep 30
      attempt=$((attempt + 1))
      continue
    fi
    break
  fi
  attempt=$((attempt + 1))
done

# ── Verify nothing lingers ────────────────────────────────────────────────────
step "Verifying no ${PROJECT}-* stacks remain"
LINGER=$(aws cloudformation list-stacks \
  --query "StackSummaries[?StackStatus!='DELETE_COMPLETE' && starts_with(StackName,\`${PROJECT}\`)].StackName" \
  --output text)
if [ -z "${LINGER}" ]; then
  ok "All ${PROJECT}-* stacks deleted. Bill stops now."
else
  fail "These stacks still exist (check console + retry):"
  echo "${LINGER}"
  exit 1
fi

echo
echo "${BOLD}════════════ Teardown complete ════════════${RESET}"
echo "  Kept for reuse: IAM role ${TASK_ROLE_NAME}, GitHub connection, Bedrock access"
echo "${BOLD}═══════════════════════════════════════════${RESET}"
