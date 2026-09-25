#!/usr/bin/env bash
#
# Every test suite, in Docker - no JDK or Node needed (make test).
#
#   ./scripts/test-all.sh [module...]     default: all modules
#
# Java modules run in a Maven container with the Docker socket shared
# (backend-core's tests start PostgreSQL with Testcontainers); the web client
# type-checks and tests in a Node container. Caches: ~/.cache/calorie-tracker.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${HOME}/.cache/calorie-tracker"
mkdir -p "${CACHE}/m2" "${CACHE}/npm"

MODULES=("$@")
[ ${#MODULES[@]} -eq 0 ] && MODULES=(eureka-server api-gateway ai-service backend-core desktop-client web-client)

DOCKER_GID="$(stat -c %g /var/run/docker.sock 2>/dev/null || stat -f %g /var/run/docker.sock)"
FAILED=()

for m in "${MODULES[@]}"; do
  echo "══ ${m} ══"
  if [ "${m}" = "web-client" ]; then
    docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e npm_config_cache=/npm \
      -v "${REPO_ROOT}/web-client:/w" -v "${CACHE}/npm:/npm" -w /w public.ecr.aws/docker/library/node:22-alpine \
      sh -c 'npm ci --no-audit --no-fund --silent && npm run -s lint && npm test' \
      || FAILED+=("${m}")
  else
    # --network host: Testcontainers publishes the database on a host port.
    docker run --rm -u "$(id -u):$(id -g)" --group-add "${DOCKER_GID}" -e HOME=/tmp \
      -e TESTCONTAINERS_RYUK_DISABLED=true --network host \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v "${REPO_ROOT}:/repo" -v "${CACHE}/m2:/m2" -w "/repo/${m}" \
      public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21 \
      mvn -B -q -Dmaven.repo.local=/m2 clean verify \
      || FAILED+=("${m}")
    cat "${REPO_ROOT}/${m}"/target/surefire-reports/*.txt 2>/dev/null | grep -E '^Tests run' || true
  fi
done

echo
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "All test suites passed: ${MODULES[*]}"
else
  echo "FAILED: ${FAILED[*]}" >&2
  exit 1
fi
