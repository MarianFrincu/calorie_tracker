#!/usr/bin/env bash
#
# Settings and helpers shared by deploy.sh, destroy.sh, smoke-test.sh and
# `make desktop-cloud`. Usage: source scripts/vars.sh
#
# Every setting can be overridden in .envrc (gitignored) or the environment;
# .envrc.example explains each one.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")/.." && pwd)"

if [ -f "${REPO_ROOT}/.envrc" ]; then
  # shellcheck disable=SC1090,SC1091
  source "${REPO_ROOT}/.envrc"
fi

# ── Settings ──────────────────────────────────────────────────────────────────
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-eu-central-1}"
export PROJECT="${PROJECT:-calorietracker}"            # prefix of every stack and resource

export DOMAIN_NAME="${DOMAIN_NAME:-}"                  # optional; needs CLOUDFRONT_CERT_ARN (us-east-1)
export CLOUDFRONT_CERT_ARN="${CLOUDFRONT_CERT_ARN:-}"

export BUDGET_EMAIL="${BUDGET_EMAIL:-}"                # optional cost alert
export BUDGET_LIMIT_USD="${BUDGET_LIMIT_USD:-10}"
export ENABLE_WAF="${ENABLE_WAF:-false}"
export MAX_TASKS="${MAX_TASKS:-3}"                     # auto-scaling ceiling per service

# AI: Gemini when a key is set, otherwise the free food-library search.
export GEMINI_API_KEY="${GEMINI_API_KEY:-}"
if [ -n "${GEMINI_API_KEY}" ]; then
  export AI_PROVIDER="${AI_PROVIDER:-gemini}"
else
  export AI_PROVIDER="${AI_PROVIDER:-library}"
fi
export GEMINI_MODEL="${GEMINI_MODEL:-gemini-3.5-flash-lite}"
export GEMINI_FALLBACK_MODEL="${GEMINI_FALLBACK_MODEL:-gemini-3.1-flash-lite}"
export AI_RATE_LIMIT_PER_MINUTE="${AI_RATE_LIMIT_PER_MINUTE:-10}"
export AI_RATE_LIMIT_PER_DAY="${AI_RATE_LIMIT_PER_DAY:-200}"

# List the versions RDS offers with:
#   aws rds describe-db-engine-versions --engine postgres --query 'DBEngineVersions[].EngineVersion'
export DB_ENGINE_VERSION="${DB_ENGINE_VERSION:-16.15}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"

export GH_BRANCH="${GH_BRANCH:-main}"                  # CI/CD only with GH_CONN_ARN + GH_REPO

# In build order: the web client needs the Cognito ids baked in.
export SERVICES_ALL="eureka-server api-gateway backend-core ai-service web-client"

# ── Derived ───────────────────────────────────────────────────────────────────
# Always the account of the credentials in use, never a value from .envrc.
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)"
export ACCOUNT_ID
export ECR="${ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"

export STACK_FOUNDATION="${PROJECT}-foundation"
export STACK_DATABASE="${PROJECT}-database"
export STACK_COGNITO="${PROJECT}-cognito"
export STACK_APP="${PROJECT}-app"
export STACK_CICD="${PROJECT}-cicd"
export CFN_DIR="${REPO_ROOT}/infra/cloudformation"

# ── Output ────────────────────────────────────────────────────────────────────
export CYAN=$'\033[0;36m' GREEN=$'\033[0;32m' YELLOW=$'\033[0;33m' RED=$'\033[0;31m' BOLD=$'\033[1m' RESET=$'\033[0m'

step() { echo "${BOLD}${CYAN}── $* ──${RESET}"; }
ok()   { echo "${GREEN}✓${RESET} $*"; }
warn() { echo "${YELLOW}⚠${RESET} $*"; }
fail() { echo "${RED}✗${RESET} $*" >&2; }

if [ -z "${ACCOUNT_ID}" ] || [ "${ACCOUNT_ID}" = "None" ]; then
  fail "Couldn't resolve your AWS account. Run 'aws configure' (or 'aws sso login') first."
  # shellcheck disable=SC2317  # reached when the file is run instead of sourced
  return 1 2>/dev/null || exit 1
fi

print_settings() {
  echo
  printf "  %-13s %s\n" "Region:" "${AWS_DEFAULT_REGION}" "Account:" "${ACCOUNT_ID}" "Prefix:" "${PROJECT}" \
    "HTTPS:" "CloudFront${DOMAIN_NAME:+ + ${DOMAIN_NAME}}" "WAF:" "${ENABLE_WAF}" \
    "Max tasks:" "${MAX_TASKS} per service" "AI provider:" "${AI_PROVIDER}" \
    "CI/CD:" "${GH_REPO:+${GH_REPO}@${GH_BRANCH}}${GH_REPO:-off (GH_CONN_ARN/GH_REPO not set)}"
  echo
}

# ── AWS helpers ───────────────────────────────────────────────────────────────

# stack_output <stack> <key> - empty when the stack or output doesn't exist.
stack_output() {
  local v
  v="$(aws cloudformation describe-stacks --stack-name "$1" \
    --query "Stacks[0].Outputs[?OutputKey=='$2'].OutputValue" --output text 2>/dev/null || true)"
  [ "${v}" = "None" ] && v=""
  echo "${v}"
}

# Parameters for 04-app.yaml, as the array APP_PARAMS.
build_app_params() {
  APP_PARAMS=(
    ProjectName="${PROJECT}"
    ImageTag="${IMAGE_TAG}"
    AiProvider="${AI_PROVIDER}"
    EnableWaf="${ENABLE_WAF}"
    MaxCount="${MAX_TASKS}"
    AiRateLimitPerMinute="${AI_RATE_LIMIT_PER_MINUTE}"
    AiRateLimitPerDay="${AI_RATE_LIMIT_PER_DAY}"
  )
  # CloudFront's origin-facing prefix list (id differs per region).
  local pl
  pl="$(aws ec2 describe-managed-prefix-lists \
    --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
    --query 'PrefixLists[0].PrefixListId' --output text 2>/dev/null || true)"
  if [ -n "${pl}" ] && [ "${pl}" != "None" ]; then
    APP_PARAMS+=(CloudFrontPrefixListId="${pl}")
  fi
  if [ -n "${DOMAIN_NAME}" ]; then
    if [ -z "${CLOUDFRONT_CERT_ARN}" ]; then
      fail "DOMAIN_NAME is set but CLOUDFRONT_CERT_ARN is empty (needs an ACM certificate in us-east-1)."
      return 1
    fi
    APP_PARAMS+=(DomainName="${DOMAIN_NAME}" CloudFrontCertificateArn="${CLOUDFRONT_CERT_ARN}")
  fi
  if [ "${AI_PROVIDER}" = "gemini" ]; then
    if [ -z "${GEMINI_API_KEY}" ]; then
      fail "AI_PROVIDER=gemini but GEMINI_API_KEY is empty. Add it to .envrc."
      return 1
    fi
    APP_PARAMS+=(GeminiApiKey="${GEMINI_API_KEY}" GeminiModel="${GEMINI_MODEL}"
                 GeminiFallbackModel="${GEMINI_FALLBACK_MODEL}")
  fi
}

# Builds and pushes one image as :IMAGE_TAG and :<commit> (":<commit>-dirty"
# when that folder has uncommitted changes).
build_and_push() {
  local svc="$1" build_args=() sha
  if [ "${svc}" = "web-client" ]; then
    local client_id pool_id
    client_id="$(stack_output "${STACK_COGNITO}" AppClientId)"
    pool_id="$(stack_output "${STACK_COGNITO}" UserPoolId)"
    if [ -z "${client_id}" ] || [ -z "${pool_id}" ]; then
      fail "Cognito stack ${STACK_COGNITO} not found - deploy it before building the web client."
      return 1
    fi
    build_args=(--build-arg VITE_AUTH_MODE=cognito
                --build-arg "VITE_COGNITO_USER_POOL_ID=${pool_id}"
                --build-arg "VITE_COGNITO_CLIENT_ID=${client_id}")
  fi
  sha="$(git -C "${REPO_ROOT}" rev-parse --short=12 HEAD 2>/dev/null || echo local)"
  if [ -n "$(git -C "${REPO_ROOT}" status --porcelain -- "${svc}" 2>/dev/null)" ]; then
    sha="${sha}-dirty"
  fi
  docker build --platform linux/amd64 "${build_args[@]}" \
    -t "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}" \
    -t "${ECR}/${PROJECT}-${svc}:${sha}" \
    "${REPO_ROOT}/${svc}"
  docker push "${ECR}/${PROJECT}-${svc}:${IMAGE_TAG}"
  docker push "${ECR}/${PROJECT}-${svc}:${sha}"
}

# ECS service name(s) for an image directory (the Eureka image runs as two).
ecs_service_for() {
  case "$1" in
    eureka-server) echo "${PROJECT}-eureka-a ${PROJECT}-eureka-b" ;;
    api-gateway)   echo "${PROJECT}-gateway" ;;
    backend-core)  echo "${PROJECT}-core" ;;
    ai-service)    echo "${PROJECT}-ai" ;;
    web-client)    echo "${PROJECT}-web" ;;
    *) return 1 ;;
  esac
}
