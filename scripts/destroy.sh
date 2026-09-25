#!/usr/bin/env bash
#
# Delete everything in AWS, in reverse dependency order (make destroy):
# app → CI/CD → Cognito → database → foundation (VPC, Cloud Map, ECR + images).
#
#   ./scripts/destroy.sh               asks you to type "destroy"
#   ./scripts/destroy.sh --yes         no prompt
#   ./scripts/destroy.sh --keep-data   keep the final database snapshot (~USD 0.10/GB-month)
#
# Left alone (free): the GitHub connection, the budget alert, service-linked roles.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

SKIP_PROMPT=0
KEEP_DATA=0
for arg in "$@"; do
  case "${arg}" in
    --yes|-y)    SKIP_PROMPT=1 ;;
    --keep-data) KEEP_DATA=1 ;;
    *) fail "Unknown option ${arg}"; exit 1 ;;
  esac
done

if [ "${SKIP_PROMPT}" = 0 ]; then
  warn "About to DELETE every ${PROJECT}-* stack in account ${ACCOUNT_ID} (${AWS_DEFAULT_REGION}):"
  echo "    ${STACK_APP}, ${STACK_CICD} (if any), ${STACK_COGNITO}, ${STACK_DATABASE}, ${STACK_FOUNDATION}"
  if [ "${KEEP_DATA}" = 1 ]; then
    echo "    The database is kept as a final snapshot."
  else
    echo "    ALL DATA is deleted, including the final database snapshot."
  fi
  read -p "Type 'destroy' to continue: " -r CONFIRM
  [ "${CONFIRM}" = destroy ] || { fail "Aborted."; exit 1; }
fi

stack_exists() {
  aws cloudformation describe-stacks --stack-name "$1" >/dev/null 2>&1
}

delete_stack() {
  if ! stack_exists "$1"; then
    ok "$1 doesn't exist"
    return
  fi
  echo "  deleting $1..."
  aws cloudformation delete-stack --stack-name "$1"
  if aws cloudformation wait stack-delete-complete --stack-name "$1" 2>/dev/null; then
    ok "$1 deleted"
  else
    warn "$1 didn't delete cleanly - see CloudFormation > $1 > Events"
  fi
}

step "App stack (Fargate, load balancer, CloudFront)"
delete_stack "${STACK_APP}"

step "CI/CD stack"
if stack_exists "${STACK_CICD}"; then
  # A bucket must be empty before CloudFormation can delete it.
  BUCKET="$(aws cloudformation describe-stack-resources --stack-name "${STACK_CICD}" \
    --logical-resource-id ArtifactBucket --query 'StackResources[0].PhysicalResourceId' --output text 2>/dev/null || true)"
  [ -n "${BUCKET}" ] && [ "${BUCKET}" != None ] && aws s3 rm "s3://${BUCKET}" --recursive >/dev/null 2>&1 || true
fi
delete_stack "${STACK_CICD}"

step "Cognito stack"
POOL_ID="$(stack_output "${STACK_COGNITO}" UserPoolId)"
if [ -n "${POOL_ID}" ]; then
  aws cognito-idp update-user-pool --user-pool-id "${POOL_ID}" --deletion-protection INACTIVE >/dev/null 2>&1 || true
fi
delete_stack "${STACK_COGNITO}"

step "Database stack (~5 min)"
if aws rds describe-db-instances --db-instance-identifier "${PROJECT}-db" >/dev/null 2>&1; then
  aws rds modify-db-instance --db-instance-identifier "${PROJECT}-db" \
    --no-deletion-protection --apply-immediately >/dev/null
  aws rds wait db-instance-available --db-instance-identifier "${PROJECT}-db" || true
fi
delete_stack "${STACK_DATABASE}"   # takes a final snapshot (DeletionPolicy: Snapshot)
SNAPSHOTS="$(aws rds describe-db-snapshots --db-instance-identifier "${PROJECT}-db" --snapshot-type manual \
  --query 'DBSnapshots[].DBSnapshotIdentifier' --output text 2>/dev/null || true)"
if [ -n "${SNAPSHOTS}" ] && [ "${SNAPSHOTS}" != None ]; then
  if [ "${KEEP_DATA}" = 1 ]; then
    warn "Kept snapshot(s): ${SNAPSHOTS} - delete them when no longer needed."
  else
    for snap in ${SNAPSHOTS}; do
      aws rds delete-db-snapshot --db-snapshot-identifier "${snap}" >/dev/null && ok "Deleted snapshot ${snap}"
    done
  fi
fi

# Leftovers that would block the network stack: Cloud Map instances of tasks
# that died without de-registering, and task network interfaces that can take
# 5-15 min to detach on their own.
step "Clean-up before the network stack"
NS_ID="$(aws servicediscovery list-namespaces \
  --query "Namespaces[?Name==\`${PROJECT}.local\`].Id" --output text 2>/dev/null || true)"
if [ -n "${NS_ID}" ] && [ "${NS_ID}" != None ]; then
  for SVC_ID in $(aws servicediscovery list-services --filters "Name=NAMESPACE_ID,Values=${NS_ID}" \
                    --query 'Services[].Id' --output text 2>/dev/null); do
    for INST_ID in $(aws servicediscovery list-instances --service-id "${SVC_ID}" \
                       --query 'Instances[].Id' --output text 2>/dev/null); do
      aws servicediscovery deregister-instance --service-id "${SVC_ID}" --instance-id "${INST_ID}" >/dev/null 2>&1 || true
    done
  done
fi
VPC_ID="$(aws cloudformation describe-stack-resources --stack-name "${STACK_FOUNDATION}" \
  --logical-resource-id Vpc --query 'StackResources[0].PhysicalResourceId' --output text 2>/dev/null || true)"
if [ -n "${VPC_ID}" ] && [ "${VPC_ID}" != None ]; then
  for ENI_ID in $(aws ec2 describe-network-interfaces \
                    --filters "Name=vpc-id,Values=${VPC_ID}" "Name=status,Values=available" \
                    --query 'NetworkInterfaces[].NetworkInterfaceId' --output text 2>/dev/null); do
    aws ec2 delete-network-interface --network-interface-id "${ENI_ID}" >/dev/null 2>&1 || true
  done
fi
ok "Done"

step "Foundation stack (VPC, ECR repos + images, Cloud Map)"
# Can fail once on network-interface / Cloud Map lag; retry up to 3 times.
for attempt in 1 2 3; do
  delete_stack "${STACK_FOUNDATION}"
  stack_exists "${STACK_FOUNDATION}" || break
  warn "Attempt ${attempt} failed:"
  # shellcheck disable=SC2016  # backticks are JMESPath, not shell
  aws cloudformation describe-stack-events --stack-name "${STACK_FOUNDATION}" \
    --query 'StackEvents[?ResourceStatus==`DELETE_FAILED`].[LogicalResourceId,ResourceStatusReason]' \
    --output table | head -20
  sleep 30
done

LINGER=$(aws cloudformation list-stacks \
  --query "StackSummaries[?StackStatus!='DELETE_COMPLETE' && starts_with(StackName,\`${PROJECT}\`)].StackName" \
  --output text)
if [ -n "${LINGER}" ]; then
  fail "Still there (check the console, then run make destroy again): ${LINGER}"
  exit 1
fi
ok "Everything deleted. Charges stop now.$([ "${KEEP_DATA}" = 1 ] && echo " Kept: the final database snapshot.")"
