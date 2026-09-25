#!/usr/bin/env bash
#
# End-to-end check of the deployed app in ~1 minute (make smoke).
#
#   ./scripts/smoke-test.sh [https://app.example.com]   default: the app stack's URL
#
# Checks HTTPS and headers, then signs in a throwaway Cognito user (admin API,
# no e-mail), uses every API area, deletes the account and the user.
# Needs the AWS CLI, curl and python3.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh"

URL="${1:-$(stack_output "${STACK_APP}" AppUrl)}"
URL="${URL%/}"
POOL_ID="$(stack_output "${STACK_COGNITO}" UserPoolId)"
CLIENT_ID="$(stack_output "${STACK_COGNITO}" AppClientId)"
if [ -z "${URL}" ]; then
  fail "No app stack found - deploy first (make deploy)."
  exit 1
fi

PASS=0
FAILED=0
check() { # $1 description, $2 expected, $3 actual
  if [ "$2" = "$3" ]; then
    printf "  ${GREEN}✓${RESET} %-52s %s\n" "$1" "$3"; PASS=$((PASS + 1))
  else
    printf "  ${RED}✗${RESET} %-52s expected %s, got %s\n" "$1" "$2" "$3"; FAILED=$((FAILED + 1))
  fi
}
json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

TOKEN=""
api() { # $1 method, $2 path, [$3 body] -> prints status; body in /tmp file
  local args=(-s -o "${BODY}" -w '%{http_code}' -X "$1" -H "X-Time-Zone: Europe/Bucharest")
  [ -n "${TOKEN}" ] && args+=(-H "Authorization: Bearer ${TOKEN}")
  [ $# -ge 3 ] && args+=(-H 'Content-Type: application/json' -d "$3")
  curl "${args[@]}" "${URL}$2"
}
BODY="$(mktemp)"
EMAIL="smoke-$(date +%s)-${RANDOM}@example.com"
PASSWORD="Smoke-$(openssl rand -hex 6)-Aa1"
cleanup() {
  rm -f "${BODY}"
  aws cognito-idp admin-delete-user --user-pool-id "${POOL_ID}" --username "${EMAIL}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

step "Edge: ${URL}"
check "GET / (web app)" 200 "$(curl -s -o /dev/null -w '%{http_code}' "${URL}/")"
HEADERS="$(curl -s -D - -o /dev/null "${URL}/")"
check "HSTS header" yes "$(grep -qi '^strict-transport-security' <<<"${HEADERS}" && echo yes || echo no)"
check "Content-Security-Policy header" yes "$(grep -qi '^content-security-policy' <<<"${HEADERS}" && echo yes || echo no)"
check "X-Frame-Options header" yes "$(grep -qi '^x-frame-options' <<<"${HEADERS}" && echo yes || echo no)"
check "http:// redirects to https://" 301 "$(curl -s -o /dev/null -w '%{http_code}' "${URL/https:/http:}/")"
check "TLS 1.2+ negotiated" yes "$(curl -s -o /dev/null --tlsv1.2 "${URL}/" && echo yes || echo no)"
check "Deep link /reports serves the app" 200 "$(curl -s -o /dev/null -w '%{http_code}' "${URL}/reports")"
check "Missing asset is a real 404" 404 "$(curl -s -o /dev/null -w '%{http_code}' "${URL}/assets/nope.js")"
check "API without a token" 401 "$(api GET /api/profile)"
CORS="$(curl -s -D - -o /dev/null -X OPTIONS -H 'Origin: https://evil.example' \
  -H 'Access-Control-Request-Method: GET' "${URL}/api/profile")"
check "No cross-origin API access" no "$(grep -qi '^access-control-allow-origin' <<<"${CORS}" && echo yes || echo no)"
SCHEME="$(aws elbv2 describe-load-balancers --names "${PROJECT}-alb" --query 'LoadBalancers[0].Scheme' --output text 2>/dev/null)"
check "Load balancer is private" internal "${SCHEME}"

step "Accounts: throwaway user ${EMAIL}"
aws cognito-idp admin-create-user --user-pool-id "${POOL_ID}" --username "${EMAIL}" \
  --user-attributes Name=email,Value="${EMAIL}" Name=email_verified,Value=true \
  --message-action SUPPRESS >/dev/null
aws cognito-idp admin-set-user-password --user-pool-id "${POOL_ID}" --username "${EMAIL}" \
  --password "${PASSWORD}" --permanent
# The apps sign in with SRP (browser/desktop code, unit-tested against AWS's
# library). Here the admin API signs the throwaway user in with its password.
AUTH="$(aws cognito-idp admin-initiate-auth --user-pool-id "${POOL_ID}" --client-id "${CLIENT_ID}" \
  --auth-flow ADMIN_USER_PASSWORD_AUTH --auth-parameters USERNAME="${EMAIL}",PASSWORD="${PASSWORD}" --output json)"
TOKEN="$(json "d['AuthenticationResult']['AccessToken']" <<<"${AUTH}")"
ID_TOKEN="$(json "d['AuthenticationResult']['IdToken']" <<<"${AUTH}")"
REFRESH="$(json "d['AuthenticationResult']['RefreshToken']" <<<"${AUTH}")"
check "Sign-in" yes "$([ -n "${TOKEN}" ] && echo yes || echo no)"
check "Plain password sign-in refused (SRP only)" refused "$(aws cognito-idp initiate-auth --client-id "${CLIENT_ID}" \
  --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME="${EMAIL}",PASSWORD="${PASSWORD}" >/dev/null 2>&1 \
  && echo allowed || echo refused)"
REFRESHED="$(aws cognito-idp initiate-auth --client-id "${CLIENT_ID}" --auth-flow REFRESH_TOKEN_AUTH \
  --auth-parameters REFRESH_TOKEN="${REFRESH}" --query 'AuthenticationResult.AccessToken' --output text 2>/dev/null)"
check "Token refresh (REFRESH_TOKEN_AUTH)" yes "$([ -n "${REFRESHED}" ] && [ "${REFRESHED}" != None ] && echo yes || echo no)"
SAVED="${TOKEN}"; TOKEN="${ID_TOKEN}"
check "ID token rejected as API credential" 401 "$(api GET /api/profile)"
TOKEN="${SAVED}"

step "API as the new user"
TODAY="$(TZ=Europe/Bucharest date +%F)"
check "First request is a read (summary)" 200 "$(api GET "/api/summary?date=${TODAY}")"
check "Save profile" 200 "$(api PUT /api/profile '{"sex":"FEMALE","age":30,"heightCm":165,"weightKg":60,"activityLevel":"LIGHT"}')"
check "Save objective" 200 "$(api PUT /api/objective '{"goal":"LOSE","goalPercent":15,"macroPreset":"BALANCED"}')"
check "Search public foods" 200 "$(api GET '/api/ingredients?scope=all&q=egg&size=1')"
EGG="$(json "d[0]['id']" <"${BODY}")"
check "Create own food" 201 "$(api POST /api/ingredients '{"name":"Smoke oats","kcalPer100g":380,"proteinPer100g":13,"carbsPer100g":67,"fatPer100g":7,"fiberPer100g":10}')"
OATS="$(json "d['id']" <"${BODY}")"
check "Create recipe" 201 "$(api POST /api/recipes "{\"name\":\"Smoke porridge\",\"ingredients\":[{\"ingredientId\":${OATS},\"amountGrams\":80},{\"ingredientId\":${EGG},\"amountGrams\":50}],\"totalCookedGrams\":250}")"
check "Add diary entry" 201 "$(api POST /api/diary "{\"date\":\"${TODAY}\",\"meal\":\"BREAKFAST\",\"ingredientId\":${EGG},\"amountGrams\":100}")"
ENTRY="$(json "d['id']" <"${BODY}")"
check "Move diary entry" 200 "$(api POST "/api/diary/${ENTRY}/move" "{\"date\":\"${TODAY}\",\"meal\":\"LUNCH\"}")"
check "Add water" 201 "$(api POST /api/water "{\"date\":\"${TODAY}\",\"ml\":250}")"
check "Log weight" 201 "$(api POST /api/weight "{\"date\":\"${TODAY}\",\"weightKg\":59.8}")"
api GET "/api/summary?date=${TODAY}" >/dev/null
check "Summary reflects the entry" yes "$(json "'yes' if d['consumedKcal']>0 and d['water']['totalMl']==250 else 'no'" <"${BODY}")"
check "Nutrition report" 200 "$(api GET /api/reports/nutrition)"
check "Malformed input is a 400, not a 500" 400 "$(api POST /api/water '{"date":')"
AI="$(api POST /api/ai/parse '{"text":"2 eggs and a banana"}')"
if [ "${AI}" = 503 ]; then
  warn "AI parse returned 503: both Gemini models are busy right now (Google's side) - re-run in a minute."
elif [ "${AI}" = 502 ]; then
  warn "AI parse returned 502: the provider refused - with Gemini, check GEMINI_API_KEY and its quota."
fi
check "AI parse (${AI_PROVIDER})" 200 "${AI}"
check "Delete account (all data)" 204 "$(api DELETE /api/profile)"

echo
if [ "${FAILED}" -eq 0 ]; then
  ok "${PASS} checks passed."
else
  fail "${FAILED} failed, ${PASS} passed."
  exit 1
fi
