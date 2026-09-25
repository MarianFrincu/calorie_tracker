#!/usr/bin/env bash
#
# Deploy to AWS. Safe to re-run: unchanged stacks are skipped.
#
#   ./scripts/deploy.sh                  everything                          (make deploy)
#   ./scripts/deploy.sh app              only 04-app.yaml / its settings     (make deploy-app)
#   ./scripts/deploy.sh images [svc...]  rebuild, push and roll images       (make deploy-images)
#
# "Everything": budget alert (optional) → 01-foundation → 02-database →
# 03-cognito → images → 04-app → 05-cicd (optional). The first run takes
# ~45 min, mostly RDS and CloudFront. Billed per hour until `make destroy`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

MODE="${1:-all}"
[ "$#" -gt 0 ] && shift

# ── Helpers ───────────────────────────────────────────────────────────────────

stack_status() {
  aws cloudformation describe-stacks --stack-name "$1" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || true
}

# A stack whose first creation failed can only be deleted, not updated.
clear_failed_stack() {
  local status
  status="$(stack_status "$1")"
  case "${status}" in
    ROLLBACK_COMPLETE|ROLLBACK_FAILED)
      warn "$1 is left over from a failed first attempt (${status}) - deleting it before retrying"
      aws cloudformation delete-stack --stack-name "$1"
      aws cloudformation wait stack-delete-complete --stack-name "$1" \
        || { fail "Could not delete $1 - see CloudFormation > $1 > Events."; exit 1; }
      ok "$1 cleared"
      ;;
  esac
}

deploy_app_stack() {
  clear_failed_stack "${STACK_APP}"
  build_app_params
  aws cloudformation deploy --no-fail-on-empty-changeset \
    --stack-name "${STACK_APP}" \
    --template-file "${CFN_DIR}/04-app.yaml" \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides "${APP_PARAMS[@]}"
}

push_images() { # $@ = image directories
  aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR}"
  for svc in "$@"; do
    echo "  ─── ${svc} ───"
    build_and_push "${svc}"
  done
}

# New tasks pull the new :latest; then wait until stable.
roll_services() { # $@ = image directories
  local ecs=() names
  for svc in "$@"; do
    read -r -a names <<< "$(ecs_service_for "${svc}")"
    ecs+=("${names[@]}")
  done
  for s in "${ecs[@]}"; do
    aws ecs update-service --cluster "${PROJECT}-cluster" --service "${s}" --force-new-deployment >/dev/null
  done
  wait_stable "${ecs[@]}"
}

wait_stable() { # $@ = ECS service names
  step "Waiting for the ECS services to become stable (~3-5 min)"
  # The waiter gives up after 10 minutes; allow two rounds.
  aws ecs wait services-stable --cluster "${PROJECT}-cluster" --services "$@" \
    || aws ecs wait services-stable --cluster "${PROJECT}-cluster" --services "$@"
  ok "All services stable"
}

all_ecs_services() {
  for svc in ${SERVICES_ALL}; do ecs_service_for "${svc}"; done
}

# Monthly budget: e-mails at 50% / 100% of actual spend and on a forecast
# above the limit. Credits aren't subtracted, so it shows real usage.
budget_alert() {
  local name="${PROJECT}-cost-guard" budget notifications="" kind pct
  budget="{\"BudgetName\":\"${name}\",\"BudgetLimit\":{\"Amount\":\"${BUDGET_LIMIT_USD}\",\"Unit\":\"USD\"},\"TimeUnit\":\"MONTHLY\",\"BudgetType\":\"COST\",\"CostTypes\":{\"IncludeCredit\":false,\"IncludeRefund\":false}}"
  if aws budgets describe-budget --account-id "${ACCOUNT_ID}" --budget-name "${name}" >/dev/null 2>&1; then
    aws budgets update-budget --account-id "${ACCOUNT_ID}" --new-budget "${budget}"
    ok "Budget ${name}: USD ${BUDGET_LIMIT_USD}/month"
    return
  fi
  for n in "ACTUAL 50" "ACTUAL 100" "FORECASTED 100"; do
    read -r kind pct <<< "${n}"
    notifications+="${notifications:+,}{\"Notification\":{\"NotificationType\":\"${kind}\",\"ComparisonOperator\":\"GREATER_THAN\",\"Threshold\":${pct},\"ThresholdType\":\"PERCENTAGE\"},\"Subscribers\":[{\"SubscriptionType\":\"EMAIL\",\"Address\":\"${BUDGET_EMAIL}\"}]}"
  done
  aws budgets create-budget --account-id "${ACCOUNT_ID}" --budget "${budget}" \
    --notifications-with-subscribers "[${notifications}]"
  ok "Budget ${name}: e-mails ${BUDGET_EMAIL} at 50% / 100% of USD ${BUDGET_LIMIT_USD}/month"
}

# A new CloudFront distribution takes a few minutes to reach every edge;
# until then some requests hang. Wait for 5 good answers in a row.
wait_for_url() {
  local url="$1" streak=0
  step "Waiting for ${url} to answer reliably (usually < 5 min)"
  for _ in $(seq 1 120); do
    if [ "$(curl -s -o /dev/null -m 10 -w '%{http_code}' "${url}/healthz")" = 200 ]; then
      streak=$((streak + 1))
      [ "${streak}" -ge 5 ] && { ok "Reachable"; return 0; }
    else
      streak=0
    fi
    sleep 5
  done
  warn "Still not answering reliably - give it a few more minutes, then run make smoke."
}

# The pipeline's first run builds GitHub's code. Creating it while the local
# code isn't pushed would deploy the older GitHub version over this one.
code_is_pushed() {
  [ -z "$(git -C "${REPO_ROOT}" status --porcelain 2>/dev/null)" ] \
    && [ "$(git -C "${REPO_ROOT}" rev-list --count '@{u}..HEAD' 2>/dev/null || echo 1)" = 0 ]
}

print_result() {
  local url
  url="$(stack_output "${STACK_APP}" AppUrl)"
  wait_for_url "${url}"
  echo
  printf "  %-15s %s\n" "Web app:" "${url}" "Cognito client:" "$(stack_output "${STACK_COGNITO}" AppClientId)"
  if [ -n "${DOMAIN_NAME}" ]; then
    echo "  Create a CNAME: ${DOMAIN_NAME} -> $(stack_output "${STACK_APP}" CloudFrontDomainName)"
  fi
  echo
  echo "Next: make smoke (end-to-end check) · make desktop-cloud · make destroy when finished"
}

# ── Modes ─────────────────────────────────────────────────────────────────────

if [ "${MODE}" != "app" ] && ! docker info >/dev/null 2>&1; then
  fail "Docker is not running (needed to build the images)"
  exit 1
fi

case "${MODE}" in
  app)
    step "Updating the app stack"
    deploy_app_stack
    # shellcheck disable=SC2046
    wait_stable $(all_ecs_services)
    print_result
    exit 0
    ;;
  images)
    if [ "$#" -gt 0 ]; then SERVICES=("$@"); else read -r -a SERVICES <<< "${SERVICES_ALL}"; fi
    for svc in "${SERVICES[@]}"; do
      ecs_service_for "${svc}" >/dev/null || { fail "Unknown service '${svc}' (one of: ${SERVICES_ALL})"; exit 1; }
    done
    step "Building + pushing: ${SERVICES[*]}"
    push_images "${SERVICES[@]}"
    roll_services "${SERVICES[@]}"
    exit 0
    ;;
  all) ;;
  *) fail "Unknown mode '${MODE}'. Use: deploy.sh [app | images [service...]]"; exit 1 ;;
esac

print_settings
build_app_params   # fail fast on a bad setting, before creating anything

# A new account may lack the ECS service-linked role, and the first service
# can race its creation. Harmless if it exists.
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com >/dev/null 2>&1 || true

if [ -n "${BUDGET_EMAIL}" ]; then
  step "Cost alert"
  budget_alert || warn "Could not create the budget alert (continuing)"
fi

step "1/6 Foundation stack (~3 min)"
clear_failed_stack "${STACK_FOUNDATION}"
aws cloudformation deploy --no-fail-on-empty-changeset \
  --stack-name "${STACK_FOUNDATION}" \
  --template-file "${CFN_DIR}/01-foundation.yaml" \
  --parameter-overrides ProjectName="${PROJECT}"

step "2/6 Database stack (~10 min)"
clear_failed_stack "${STACK_DATABASE}"
aws cloudformation deploy --no-fail-on-empty-changeset \
  --stack-name "${STACK_DATABASE}" \
  --template-file "${CFN_DIR}/02-database.yaml" \
  --parameter-overrides ProjectName="${PROJECT}" DBEngineVersion="${DB_ENGINE_VERSION}"

step "3/6 Cognito stack (~1 min)"
clear_failed_stack "${STACK_COGNITO}"
aws cloudformation deploy --no-fail-on-empty-changeset \
  --stack-name "${STACK_COGNITO}" \
  --template-file "${CFN_DIR}/03-cognito.yaml" \
  --parameter-overrides ProjectName="${PROJECT}"

step "4/6 Building + pushing images (~10 min)"
# shellcheck disable=SC2086
push_images ${SERVICES_ALL}

APP_EXISTED=false
case "$(stack_status "${STACK_APP}")" in
  CREATE_COMPLETE|UPDATE_COMPLETE|UPDATE_ROLLBACK_COMPLETE) APP_EXISTED=true ;;
esac

step "5/6 App stack (~20 min the first time)"
deploy_app_stack
if [ "${APP_EXISTED}" = true ]; then
  # Unchanged task definitions restart nothing: roll so the new images run.
  # shellcheck disable=SC2086
  roll_services ${SERVICES_ALL}
else
  # shellcheck disable=SC2046
  wait_stable $(all_ecs_services)
fi

if [ -z "${GH_CONN_ARN:-}" ] || [ -z "${GH_REPO:-}" ]; then
  warn "6/6 CI/CD skipped (set GH_CONN_ARN and GH_REPO in .envrc to enable it)"
elif [ -z "$(stack_status "${STACK_CICD}")" ] && ! code_is_pushed; then
  warn "6/6 CI/CD not created: commit and push first. Its first run deploys GitHub's ${GH_BRANCH},"
  warn "    which would replace what was just deployed with older code. Then run make deploy again."
else
  step "6/6 CI/CD stack (~1 min)"
  clear_failed_stack "${STACK_CICD}"
  aws cloudformation deploy --no-fail-on-empty-changeset \
    --stack-name "${STACK_CICD}" \
    --template-file "${CFN_DIR}/05-cicd.yaml" \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides ProjectName="${PROJECT}" GitHubConnectionArn="${GH_CONN_ARN}" \
                          GitHubFullRepo="${GH_REPO}" GitHubBranch="${GH_BRANCH}"
fi

print_result
