#!/usr/bin/env bash
#
# scripts/run-ui.sh — launch the JavaFX desktop client with the right config.
#
# Looks up the ALB URL + Cognito app-client id from your CloudFormation stacks
# and passes them to the JavaFX app as environment variables (which
# AppConfig.java reads automatically). No -D flags to compose, nothing in
# config.properties, no copy-paste.
#
# If your cloud stacks aren't deployed yet, falls back to the local-dev
# defaults bundled in config.properties (localhost:8080, no auth) so you can
# develop against `docker compose up`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/vars.sh" >/dev/null

cd "${REPO_ROOT}"

# Probe CloudFormation. Empty result = stack not deployed; we fall through
# to local-dev defaults from config.properties.
API_URL="$(aws cloudformation describe-stacks --stack-name "${STACK_APP}" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" \
  --output text 2>/dev/null || true)"
APP_CLIENT="$(aws cloudformation describe-stacks --stack-name "${STACK_COGNITO}" \
  --query "Stacks[0].Outputs[?OutputKey=='AppClientId'].OutputValue" \
  --output text 2>/dev/null || true)"

if [ -n "${API_URL}" ] && [ "${API_URL}" != "None" ] \
   && [ -n "${APP_CLIENT}" ] && [ "${APP_CLIENT}" != "None" ]; then
  echo "Cloud config detected — launching against your deployed stack:"
  echo "  API URL:        ${API_URL}"
  echo "  Cognito client: ${APP_CLIENT}"
  echo "  Region:         ${AWS_DEFAULT_REGION}"
  export API_BASE_URL="${API_URL}"
  export AUTH_MODE="cognito"
  export COGNITO_REGION="${AWS_DEFAULT_REGION}"
  export COGNITO_CLIENT_ID="${APP_CLIENT}"
else
  echo "No deployed app/cognito stacks found — using local-dev defaults"
  echo "(make sure 'docker compose up' is running)."
fi

mvn -f desktop-client/pom.xml -q javafx:run
