#!/usr/bin/env bash
#
# scripts/vars.sh — single source of truth for deploy/destroy scripts.
#
# Usage:
#   source scripts/vars.sh
#
# Knobs you might want to change live in the "Configurable" section below.
# Secrets (your GitHub connection ARN, etc.) live in .envrc at the repo root —
# that file is gitignored, this one is committed.

# Resolve repo root regardless of where this is sourced from.
_VARS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "${_VARS_DIR}/.." && pwd)"

# ── Load secrets from .envrc (gitignored) if present ──────────────────────────
if [ -f "${REPO_ROOT}/.envrc" ]; then
  # shellcheck disable=SC1090,SC1091
  source "${REPO_ROOT}/.envrc"
fi

# ── Configurable ──────────────────────────────────────────────────────────────
# Pick a Bedrock-enabled AWS region. eu-central-1 (Frankfurt) and us-east-1
# are both safe defaults.
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-eu-central-1}"

# Stack-name prefix. Every resource is named "${PROJECT}-<thing>".
export PROJECT="${PROJECT:-calorietracker}"

# IAM role used by ECS tasks. Created once via deploy-all.sh, reused forever.
export TASK_ROLE_NAME="${TASK_ROLE_NAME:-CalorieTrackerTaskRole}"

# AI provider:
#   mock     — offline lookup table (no AWS, no external API)
#   bedrock  — AWS Bedrock + Claude (requires model access in the deploy region)
#   gemini   — Google Gemini API (requires GEMINI_API_KEY, used as Bedrock fallback)
export AI_PROVIDER="${AI_PROVIDER:-bedrock}"

# Gemini fallback knobs. GEMINI_API_KEY must be set in .envrc (gitignored)
# if you ever deploy with AI_PROVIDER=gemini. Get one at
# https://aistudio.google.com/app/apikey.
export GEMINI_MODEL="${GEMINI_MODEL:-gemini-3.1-flash-lite}"
# GEMINI_API_KEY is intentionally not defaulted here — it MUST come from
# .envrc so it never enters git. Empty when AI_PROVIDER != gemini is fine.
export GEMINI_API_KEY="${GEMINI_API_KEY:-}"

# Bedrock model id. MUST be an inference profile id for Claude 4+ models;
# Anthropic released them as inference-profile-only on Bedrock. Format depends
# on region — the prefix (eu./us./apac./global.) identifies which inference
# profile to use:
#
#   eu-central-1:  eu.anthropic.claude-haiku-4-5-20251001-v1:0   (cheapest, fast)
#                  eu.anthropic.claude-sonnet-4-5-20250929-v1:0  (better quality)
#                  eu.anthropic.claude-3-5-sonnet-20240620-v1:0  (legacy, direct-invoke OK)
#   us-east-1:     us.anthropic.claude-haiku-4-5-20251001-v1:0
#                  us.anthropic.claude-sonnet-4-5-20250929-v1:0
#                  us.anthropic.claude-3-5-sonnet-20240620-v1:0
#
# Discover what's available in your region:
#   aws bedrock list-inference-profiles --region <your-region> \
#     --query "inferenceProfileSummaries[?contains(inferenceProfileName,'Claude')].inferenceProfileId" \
#     --output text
#
# Only used when AI_PROVIDER=bedrock.
export BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-eu.anthropic.claude-haiku-4-5-20251001-v1:0}"

# PostgreSQL engine version. AWS rotates which patch versions are valid;
# discover current ones with:
#   aws rds describe-db-engine-versions --engine postgres \
#     --query 'DBEngineVersions[?starts_with(EngineVersion,`16.`)].EngineVersion' \
#     --output text | tr '\t' '\n' | sort -V | tail -5
export DB_ENGINE_VERSION="${DB_ENGINE_VERSION:-16.9}"

# ECR image tag the app stack pulls.
export IMAGE_TAG="${IMAGE_TAG:-latest}"

# GitHub CI/CD (skip these to skip the CI/CD stack):
#   GH_CONN_ARN  — CodeStar Connection ARN (set in .envrc)
#   GH_REPO      — owner/repo string       (set in .envrc)
#   GH_BRANCH    — defaults to main
export GH_BRANCH="${GH_BRANCH:-main}"

# ── Derived (don't edit) ──────────────────────────────────────────────────────
# Always use ${braces} + double-quotes — zsh's :r and :l history modifiers
# will mangle anything that goes $VAR:literal without braces.
export ACCOUNT_ID="${ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text 2>/dev/null)}"
export ECR="${ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"
export TASK_ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${TASK_ROLE_NAME}"

# Stack names — single source of truth.
export STACK_FOUNDATION="${PROJECT}-foundation"
export STACK_DATABASE="${PROJECT}-database"
export STACK_COGNITO="${PROJECT}-cognito"
export STACK_APP="${PROJECT}-app"
export STACK_CICD="${PROJECT}-cicd"

# Path to CFN templates relative to the repo root.
export CFN_DIR="${REPO_ROOT}/infra/cloudformation"

# ── Colors for the deploy/destroy scripts ─────────────────────────────────────
export CYAN=$'\033[0;36m'
export GREEN=$'\033[0;32m'
export YELLOW=$'\033[0;33m'
export RED=$'\033[0;31m'
export BOLD=$'\033[1m'
export RESET=$'\033[0m'

step()  { echo "${BOLD}${CYAN}── $* ──${RESET}"; }
ok()    { echo "${GREEN}✓${RESET} $*"; }
warn()  { echo "${YELLOW}⚠${RESET} $*"; }
fail()  { echo "${RED}✗${RESET} $*" >&2; }

# ── Sanity checks ─────────────────────────────────────────────────────────────
if [ -z "${ACCOUNT_ID}" ] || [ "${ACCOUNT_ID}" = "None" ]; then
  fail "Couldn't resolve AWS account id. Did you run 'aws configure'?"
  return 1 2>/dev/null || exit 1
fi

if [[ "${TASK_ROLE_ARN}" != *":role/"* ]]; then
  fail "TASK_ROLE_ARN is mangled: ${TASK_ROLE_ARN}"
  fail "Check .envrc — every \$VAR followed by ':' must use \${VAR} braces (zsh modifier bug)."
  return 1 2>/dev/null || exit 1
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo
echo "${BOLD}═══════════ Deploy variables ═══════════${RESET}"
printf "  %-20s %s\n" "Region:"         "${AWS_DEFAULT_REGION}"
printf "  %-20s %s\n" "Account:"        "${ACCOUNT_ID}"
printf "  %-20s %s\n" "Project prefix:" "${PROJECT}"
printf "  %-20s %s\n" "ECR registry:"   "${ECR}"
printf "  %-20s %s\n" "Task role:"      "${TASK_ROLE_ARN}"
printf "  %-20s %s\n" "AI provider:"    "${AI_PROVIDER}"
if [ "${AI_PROVIDER}" = "bedrock" ]; then
  printf "  %-20s %s\n" "Bedrock model:"  "${BEDROCK_MODEL_ID}"
fi
printf "  %-20s %s\n" "DB version:"     "${DB_ENGINE_VERSION}"
printf "  %-20s %s\n" "Image tag:"      "${IMAGE_TAG}"
printf "  %-20s %s\n" "GH connection:"  "${GH_CONN_ARN:-<not set — CI/CD skipped>}"
printf "  %-20s %s\n" "GH repo:"        "${GH_REPO:-<not set>}"
printf "  %-20s %s\n" "GH branch:"      "${GH_BRANCH}"
echo "${BOLD}════════════════════════════════════════${RESET}"
echo
