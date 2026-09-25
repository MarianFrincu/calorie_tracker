#!/usr/bin/env bash
#
# Native desktop app with a bundled Java runtime (make package-desktop).
# jpackage builds only for the OS it runs on.
#
#   ./scripts/package-desktop.sh [app-image | deb | rpm | msi | exe | dmg | pkg]
#
# Point it at your deployment first: set api.base.url, auth.mode=cognito,
# cognito.user.pool.id and cognito.client.id in
# desktop-client/src/main/resources/config.properties (or let users set the
# API_BASE_URL / AUTH_MODE / COGNITO_* environment variables).
#
# Optional signing: MAC_SIGNING_IDENTITY (macOS), WINDOWS_SIGN_COMMAND (the
# installer path is appended), GPG_SIGNING_KEY (Linux, detached .asc).
# A SHA-256 checksum is always written.

set -euo pipefail

TYPE="${1:-app-image}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_VERSION="${APP_VERSION:-1.0.0}"
OUT="${REPO_ROOT}/desktop-client/target/installer"
STAGE="${REPO_ROOT}/desktop-client/target/jpackage-input"

command -v jpackage >/dev/null || { echo "error: jpackage not found - use a full JDK 21+" >&2; exit 1; }

echo "==> Building desktop-client (tests included)"
mvn -B -q -f "${REPO_ROOT}/desktop-client/pom.xml" package

# Trimmed runtime (JavaFX comes from lib/). jdk.crypto.ec and jdk.jsobject
# exist only in older JDKs (21), so add them only when present.
MODULES=java.base,java.desktop,java.logging,java.net.http,java.scripting,java.xml,jdk.unsupported,jdk.xml.dom
JDK_MODULES="$("$(dirname "$(command -v jpackage)")/java" --list-modules)"
for m in jdk.crypto.ec jdk.jsobject; do
  if grep -q "^${m}@" <<< "${JDK_MODULES}"; then MODULES+=",${m}"; fi
done

echo "==> Staging jars"
rm -rf "${STAGE}" "${OUT}"
mkdir -p "${STAGE}/lib" "${OUT}"
cp "${REPO_ROOT}/desktop-client/target/desktop-client.jar" "${STAGE}/"
cp "${REPO_ROOT}/desktop-client/target/lib/"*.jar "${STAGE}/lib/"

ARGS=(
  --type "${TYPE}"
  --name "CalorieTracker"
  --app-version "${APP_VERSION}"
  --vendor "Calorie Tracker"
  --description "Calorie and macro tracker"
  --input "${STAGE}"
  --main-jar desktop-client.jar
  --main-class com.calorietracker.desktop.Launcher
  --dest "${OUT}"
  --add-modules "${MODULES}"
  --jlink-options "--strip-debug --no-header-files --no-man-pages"
  --java-options "--enable-native-access=ALL-UNNAMED"
)

case "${TYPE}" in
  deb|rpm) ARGS+=(--linux-shortcut --linux-menu-group "Utility") ;;
  msi|exe) ARGS+=(--win-menu --win-shortcut --win-dir-chooser) ;;
  dmg|pkg)
    if [ -n "${MAC_SIGNING_IDENTITY:-}" ]; then
      ARGS+=(--mac-sign --mac-signing-key-user-name "${MAC_SIGNING_IDENTITY}")
    else
      echo "warning: MAC_SIGNING_IDENTITY not set - the app will be unsigned and Gatekeeper will block it" >&2
    fi
    ;;
esac

echo "==> jpackage --type ${TYPE}"
jpackage "${ARGS[@]}"

# Windows signing happens on the finished installer.
if [[ "${TYPE}" == msi || "${TYPE}" == exe ]]; then
  if [ -n "${WINDOWS_SIGN_COMMAND:-}" ]; then
    for f in "${OUT}"/*."${TYPE}"; do
      echo "==> Signing ${f}"
      # shellcheck disable=SC2086
      ${WINDOWS_SIGN_COMMAND} "${f}"
    done
  else
    echo "warning: WINDOWS_SIGN_COMMAND not set - SmartScreen will warn users about an unsigned installer" >&2
  fi
fi

if [ "${TYPE}" != app-image ]; then
  echo "==> Checksums"
  (cd "${OUT}" && for f in *; do
    [ -f "${f}" ] || continue
    sha256sum "${f}" > "${f}.sha256" 2>/dev/null || shasum -a 256 "${f}" > "${f}.sha256"
    if [ -n "${GPG_SIGNING_KEY:-}" ]; then
      gpg --batch --yes --local-user "${GPG_SIGNING_KEY}" --armor --detach-sign "${f}"
    fi
  done)
fi

echo
echo "Done: ${OUT}"
ls -la "${OUT}"
