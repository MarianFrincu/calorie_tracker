#!/usr/bin/env bash
#
# scripts/delete-user.sh — wipe a user out forever.
#
# Removes the user's row in app_users; ON DELETE CASCADE foreign keys take
# care of diary, recipes, ingredients, weight log, water log, and objective
# history. In --cloud mode also calls cognito-idp admin-delete-user so the
# email can be re-registered.
#
# Usage:
#   ./scripts/delete-user.sh --local --user-key=dev-user
#   ./scripts/delete-user.sh --cloud --user-key=<cognito-sub>
#   ./scripts/delete-user.sh --cloud --email=you@example.com
#
# Notes:
#   - --user-key is the value of app_users.user_key (Cognito 'sub' in the
#     cloud, the literal "dev-user" locally).
#   - --email is a cloud-only convenience: resolves to the Cognito sub via
#     admin-get-user before running.
#   - Always asks for confirmation. No --force flag because this is destructive
#     and a typo can wipe the wrong account.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh" >/dev/null

MODE=""
USER_KEY=""
EMAIL=""

for arg in "$@"; do
  case "$arg" in
    --local)         MODE="local" ;;
    --cloud)         MODE="cloud" ;;
    --user-key=*)    USER_KEY="${arg#--user-key=}" ;;
    --email=*)       EMAIL="${arg#--email=}" ;;
    -h|--help)
      sed -n '3,22p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      fail "Unknown argument: ${arg}"
      exit 1
      ;;
  esac
done

if [ -z "${MODE}" ]; then
  fail "Pick one: --local or --cloud"
  exit 1
fi

if [ -z "${USER_KEY}" ] && [ -z "${EMAIL}" ]; then
  fail "Pass --user-key=<key> (or --email=<addr> in cloud mode)"
  exit 1
fi

# ── Cloud: resolve --email to sub, then run admin-delete-user + RDS DELETE. ──
if [ "${MODE}" = "cloud" ]; then
  step "Resolving Cognito user pool"
  POOL_ID="$(aws cognito-idp list-user-pools --max-results 60 \
    --query "UserPools[?Name=='${PROJECT}-userpool'].Id | [0]" --output text)"
  if [ -z "${POOL_ID}" ] || [ "${POOL_ID}" = "None" ]; then
    fail "No Cognito pool named ${PROJECT}-userpool. Is the cognito stack deployed?"
    exit 1
  fi
  ok "Pool: ${POOL_ID}"

  if [ -n "${EMAIL}" ]; then
    step "Resolving email -> Cognito sub"
    SUB="$(aws cognito-idp list-users --user-pool-id "${POOL_ID}" \
      --filter "email = \"${EMAIL}\"" \
      --query "Users[0].Attributes[?Name=='sub'].Value | [0]" --output text)"
    USERNAME="$(aws cognito-idp list-users --user-pool-id "${POOL_ID}" \
      --filter "email = \"${EMAIL}\"" \
      --query "Users[0].Username" --output text)"
    if [ -z "${SUB}" ] || [ "${SUB}" = "None" ]; then
      fail "No Cognito user with email ${EMAIL}"
      exit 1
    fi
    USER_KEY="${SUB}"
    ok "sub=${SUB}  username=${USERNAME}"
  else
    USERNAME="${USER_KEY}"
  fi

  warn "About to:"
  echo "    1. DELETE FROM app_users WHERE user_key = '${USER_KEY}'  (RDS, cascades)"
  echo "    2. cognito-idp admin-delete-user --username '${USERNAME}' (pool ${POOL_ID})"
  read -r -p "Type DELETE to confirm: " CONFIRM
  [ "${CONFIRM}" = "DELETE" ] || { warn "Aborted."; exit 0; }

  step "Fetching RDS endpoint + DB secret"
  DB_ENDPOINT="$(aws cloudformation describe-stacks --stack-name "${STACK_DATABASE}" \
    --query "Stacks[0].Outputs[?OutputKey=='DbEndpoint'].OutputValue" --output text)"
  DB_SECRET_ARN="$(aws cloudformation describe-stacks --stack-name "${STACK_DATABASE}" \
    --query "Stacks[0].Outputs[?OutputKey=='DbSecretArn'].OutputValue" --output text)"
  if [ -z "${DB_ENDPOINT}" ] || [ "${DB_ENDPOINT}" = "None" ]; then
    fail "Couldn't read DB endpoint from ${STACK_DATABASE} outputs"
    exit 1
  fi
  ok "DB at ${DB_ENDPOINT}"

  step "Running DELETE in RDS"
  DB_JSON="$(aws secretsmanager get-secret-value --secret-id "${DB_SECRET_ARN}" \
    --query SecretString --output text)"
  DB_USER="$(echo "${DB_JSON}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["username"])')"
  DB_PASS="$(echo "${DB_JSON}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["password"])')"
  PGPASSWORD="${DB_PASS}" psql \
    -h "${DB_ENDPOINT}" -U "${DB_USER}" -d calorietracker \
    -v ON_ERROR_STOP=1 \
    -c "DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE owner_user_id = (SELECT id FROM app_users WHERE user_key = '${USER_KEY}'));
        DELETE FROM recipe_ingredients WHERE ingredient_id IN (SELECT id FROM ingredients WHERE owner_user_id = (SELECT id FROM app_users WHERE user_key = '${USER_KEY}'));
        DELETE FROM app_users WHERE user_key = '${USER_KEY}';"
  ok "RDS row gone"

  step "Deleting from Cognito"
  aws cognito-idp admin-delete-user \
    --user-pool-id "${POOL_ID}" \
    --username "${USERNAME}" \
    && ok "Cognito user deleted" \
    || warn "Cognito delete failed (user may not exist there). DB row is gone."

  echo
  ok "User ${USER_KEY} wiped."
  exit 0
fi

# ── Local: docker compose postgres. ──
if [ "${MODE}" = "local" ]; then
  if [ -n "${EMAIL}" ] && [ -z "${USER_KEY}" ]; then
    fail "--email only works in --cloud mode. Locally use --user-key (likely dev-user)."
    exit 1
  fi

  if ! docker compose ps postgres >/dev/null 2>&1; then
    fail "docker compose is not running. Start it with 'docker compose up -d' first."
    exit 1
  fi

  warn "About to: DELETE FROM app_users WHERE user_key = '${USER_KEY}' (local docker postgres, cascades)"
  read -r -p "Type DELETE to confirm: " CONFIRM
  [ "${CONFIRM}" = "DELETE" ] || { warn "Aborted."; exit 0; }

  step "Running DELETE in local postgres"
  docker compose exec -T postgres \
    psql -U calorie -d calorietracker -v ON_ERROR_STOP=1 \
    -c "DELETE FROM recipe_ingredients WHERE recipe_id IN (SELECT id FROM recipes WHERE owner_user_id = (SELECT id FROM app_users WHERE user_key = '${USER_KEY}'));
        DELETE FROM recipe_ingredients WHERE ingredient_id IN (SELECT id FROM ingredients WHERE owner_user_id = (SELECT id FROM app_users WHERE user_key = '${USER_KEY}'));
        DELETE FROM app_users WHERE user_key = '${USER_KEY}';"
  ok "Local row gone"
  echo
  ok "User ${USER_KEY} wiped locally."
  exit 0
fi
