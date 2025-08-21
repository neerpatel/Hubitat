#!/usr/bin/env bash

set -euo pipefail

# Enrich collected HubSpace samples by downloading per-device versions for all devices
# in the collected metadevices list.
#
# Requires credentials (fresh token) since prior tokens are redacted.
#
# Usage:
#   HUBSPACE_USERNAME="you@example.com" HUBSPACE_PASSWORD="secret" \
#   ./scripts/enrich_afero_samples.sh
#
# Optional:
#   AFERO_CLIENT=hubspace|myko  (default hubspace)
#   IN_JSON=research/05_metadevices_with_state.json

AFERO_CLIENT="${AFERO_CLIENT:-hubspace}"
IN_JSON="${IN_JSON:-research/05_metadevices_with_state.json}"

case "$AFERO_CLIENT" in
  hubspace)
    AUTH_HOST="accounts.hubspaceconnect.com"
    AUTH_REALM="thd"
    CLIENT_ID="hubspace_android"
    REDIRECT_URI="hubspace-app://loginredirect"
    API_HOST="api2.afero.net"
    ;;
  myko)
    AUTH_HOST="accounts.mykoapp.com"
    AUTH_REALM="kfi"
    CLIENT_ID="kfi_android"
    REDIRECT_URI="kfi-app://loginredirect"
    API_HOST="api2.sxz2xlhh.afero.net"
    ;;
  *)
    echo "Unknown AFERO_CLIENT: $AFERO_CLIENT (expected hubspace|myko)" >&2
    exit 2
    ;;
esac

USERNAME="${HUBSPACE_USERNAME:-}"
PASSWORD="${HUBSPACE_PASSWORD:-}"
if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Set HUBSPACE_USERNAME and HUBSPACE_PASSWORD env vars." >&2
  exit 1
fi

command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

if [[ ! -f "$IN_JSON" ]]; then
  echo "Input JSON not found: $IN_JSON" >&2
  exit 1
fi

RESEARCH_DIR="$(pwd)/research"
VERSIONS_DIR="$RESEARCH_DIR/versions"
mkdir -p "$VERSIONS_DIR"

USER_AGENT="Dart/3.1 (dart:io)"
WORKDIR="$(mktemp -d)"
COOKIE_JAR="$WORKDIR/cookies.txt"

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

CODE_VERIFIER=$(head -c 40 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=' | tr -cd '[:alnum:]')
CODE_CHALLENGE=$(printf "%s" "$CODE_VERIFIER" | openssl dgst -binary -sha256 | base64url)

AUTH_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/auth"
LOGIN_HTML="$WORKDIR/login.html"
curl -sS -G "$AUTH_URL" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "User-Agent: $USER_AGENT" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "scope=openid offline_access" \
  --data-urlencode "code_challenge=$CODE_CHALLENGE" \
  --data-urlencode "code_challenge_method=S256" \
  -o "$LOGIN_HTML"

FORM_ACTION=$(grep -Eo 'action="[^"]*login-actions/authenticate[^"]*"' "$LOGIN_HTML" | head -n1 | sed -E 's/^action="(.*)"$/\1/' | sed -e 's/&amp;/\&/g')
if [[ -z "$FORM_ACTION" ]]; then
  SESSION_CODE=$(grep -Eo 'name="session_code" value="[^"]*"' "$LOGIN_HTML" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  EXECUTION=$(grep -Eo 'name="execution" value="[^"]*"' "$LOGIN_HTML" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  TAB_ID=$(grep -Eo 'name="tab_id" value="[^"]*"' "$LOGIN_HTML" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  FORM_ACTION="https://$AUTH_HOST/auth/realms/$AUTH_REALM/login-actions/authenticate?session_code=$SESSION_CODE&execution=$EXECUTION&client_id=$CLIENT_ID&tab_id=$TAB_ID"
fi

HDR_LOGIN="$WORKDIR/h_login.txt"
curl -sS -D "$HDR_LOGIN" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -X POST "$FORM_ACTION" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" \
  --data-urlencode "credentialId=" \
  -o /dev/null

LOCATION=$(grep -i '^Location:' "$HDR_LOGIN" | awk '{ $1=""; sub(/^ /,""); print }' | tr -d '\r\n')
CODE=$(echo "$LOCATION" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
TOKEN_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/token"
TOKEN_JSON=$(curl -sS -X POST "$TOKEN_URL" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "code_verifier=$CODE_VERIFIER")
ACCESS_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.access_token')
if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then
  echo "Failed to get access_token" >&2
  exit 1
fi

ME_JSON=$(curl -sS -X GET "https://$API_HOST/v1/users/me" -H "Authorization: Bearer $ACCESS_TOKEN")
ACCOUNT_ID=$(echo "$ME_JSON" | jq -r '.accountAccess[0].account.accountId')
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
  echo "Failed to resolve accountId" >&2
  exit 1
fi

echo "Collecting versions for devices listed in $IN_JSON"
mapfile -t IDS < <(jq -r 'map(select(.typeId=="metadevice.device")) | .[] | .deviceId // empty' "$IN_JSON")

for id in "${IDS[@]}"; do
  out="$VERSIONS_DIR/${id}.json"
  if [[ -f "$out" ]]; then
    echo "- skip $id (exists)"
    continue
  fi
  echo "- fetch $id"
  curl -sS -X GET "https://$API_HOST/v1/accounts/$ACCOUNT_ID/devices/$id/versions" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -o "$out" || true
done

echo "Done. Versions in $VERSIONS_DIR"

