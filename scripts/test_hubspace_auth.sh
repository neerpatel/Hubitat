#!/usr/bin/env bash

set -euo pipefail

# HubSpace Auth + API test via curl
# Usage:
#   HUBSPACE_USERNAME="us@meagan-neer.com" HUBSPACE_PASSWORD="change your password" ./scripts/test_hubspace_auth.sh
#
# Optional:
#   AFERO_CLIENT=hubspace (default)
#

AFERO_CLIENT="${AFERO_CLIENT:-hubspace}"

AUTH_HOST="accounts.hubspaceconnect.com"
AUTH_REALM="thd"
CLIENT_ID="hubspace_android"
REDIRECT_URI="hubspace-app://loginredirect"
USER_AGENT="Dart/3.1 (dart:io)"

API_HOST="api2.afero.net"
DATA_HOST="semantics2.afero.net"

USERNAME="${HUBSPACE_USERNAME:-}"
PASSWORD="${HUBSPACE_PASSWORD:-}"

if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Set HUBSPACE_USERNAME and HUBSPACE_PASSWORD env vars." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
COOKIE_JAR="$WORKDIR/cookies.txt"
HDR1="$WORKDIR/hdr1.txt"
HDR2="$WORKDIR/hdr2.txt"
BODY1="$WORKDIR/body1.html"
BODY2="$WORKDIR/body2.txt"

base64url() {
  # URL-safe base64 without padding
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

# Generate PKCE verifier and challenge (similar to aioafero)
CODE_VERIFIER=$(head -c 40 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=' | tr -cd '[:alnum:]')
CHALLENGE=$(printf "%s" "$CODE_VERIFIER" | openssl dgst -binary -sha256 | base64url)

echo "[1/6] GET OpenID login page (to extract session params)"
AUTH_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/auth"
curl -sS -D "$HDR1" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -G "$AUTH_URL" \
  -H "User-Agent: $USER_AGENT" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "scope=openid offline_access" \
  --data-urlencode "code_challenge=$CHALLENGE" \
  --data-urlencode "code_challenge_method=S256" \
  -o "$BODY1"

# Parse form action
FORM_ACTION=$(grep -Eo 'action="[^"]*login-actions/authenticate[^"]*"' "$BODY1" | head -n1 | sed -E 's/^action="(.*)"$/\1/')
if [[ -z "$FORM_ACTION" ]]; then
  echo "Failed to find login form action; dumping head of response:" >&2
  head -n 40 "$BODY1" >&2
  exit 1
fi
echo "Form action: $FORM_ACTION"

# Decode HTML entities (&amp; -> &)
FORM_ACTION_DECODED=$(printf "%s" "$FORM_ACTION" | sed -e 's/&amp;/\&/g')

# Extract session components
SESSION_CODE=$(echo "$FORM_ACTION_DECODED" | sed -n 's/.*[?&]session_code=\([^&]*\).*/\1/p')
EXECUTION=$(echo "$FORM_ACTION_DECODED" | sed -n 's/.*[?&]execution=\([^&]*\).*/\1/p')
TAB_ID=$(echo "$FORM_ACTION_DECODED" | sed -n 's/.*[?&]tab_id=\([^&]*\).*/\1/p')

echo "Session: code=$SESSION_CODE execution=$EXECUTION tab=$TAB_ID"
if [[ -z "$SESSION_CODE" || -z "$EXECUTION" || -z "$TAB_ID" ]]; then
  echo "Missing session parameters in action URL; attempting to parse hidden inputs..." >&2
  # Fallback: parse hidden inputs
  SESSION_CODE=$(grep -Eo 'name="session_code" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  EXECUTION=$(grep -Eo 'name="execution" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  TAB_ID=$(grep -Eo 'name="tab_id" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  echo "Session (parsed): code=$SESSION_CODE execution=$EXECUTION tab=$TAB_ID"
  if [[ -z "$SESSION_CODE" || -z "$EXECUTION" || -z "$TAB_ID" ]]; then
    echo "Missing session parameters; cannot continue." >&2
    exit 1
  fi
fi

echo "[2/6] POST login (expect 302 with code)"
LOGIN_URL="$FORM_ACTION_DECODED"
curl -sS -D "$HDR2" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -X POST "$LOGIN_URL" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" \
  --data-urlencode "credentialId=" \
  -o "$BODY2"

LOCATION=$(grep -i '^Location:' "$HDR2" | awk '{ $1=""; sub(/^ /,""); print }' | tr -d '\r\n')
HTTP_STATUS=$(grep -i '^HTTP/' "$HDR2" | tail -n1 | awk '{print $2}')
echo "HTTP status: $HTTP_STATUS"
echo "Location: $LOCATION"

CODE=$(echo "$LOCATION" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
if [[ -z "$CODE" ]]; then
  echo "No authorization code found; server said $HTTP_STATUS. Body (tail):" >&2
  tail -n 40 "$BODY2" >&2
  exit 1
fi
echo "Auth code: $CODE"

echo "[3/6] Exchange code for tokens"
TOKEN_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/token"
TOKEN_JSON=$(curl -sS -X POST "$TOKEN_URL" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Accept: application/json" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code=$CODE" \
  --data-urlencode "code_verifier=$CODE_VERIFIER")

ACCESS_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.access_token' 2>/dev/null || true)
REFRESH_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.refresh_token' 2>/dev/null || true)
if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then
  echo "Token response (raw): $TOKEN_JSON" >&2
  echo "Failed to extract access_token. Install jq for better parsing." >&2
  exit 1
fi
echo "Access token acquired (length ${#ACCESS_TOKEN})"

echo "[4/6] GET account id"
ME_JSON=$(curl -sS -X GET "https://$API_HOST/v1/users/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Host: $API_HOST")

ACCOUNT_ID=$(echo "$ME_JSON" | jq -r '.accountAccess[0].account.accountId' 2>/dev/null || true)
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
  echo "Response (raw): $ME_JSON" >&2
  echo "Failed to extract accountId."
  exit 1
fi
echo "Account ID: $ACCOUNT_ID"

echo "[5/6] List devices (with state)"
DEVICES=$(curl -sS -X GET "https://$API_HOST/v1/accounts/$ACCOUNT_ID/metadevices?expansions=state" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Host: $DATA_HOST")

COUNT=$(echo "$DEVICES" | jq 'length' 2>/dev/null || echo "n/a")
echo "Devices returned: $COUNT"

echo "[5b/6] Devices list (id | class | name)"
echo "$DEVICES" | jq -r '
  .[]
  | {
      id: (.deviceId // .id // .metadeviceId // .device_id),
      class: (.description.device.deviceClass // .device_class // .typeId // .description.deviceClass // .type),
      name: (.description.device.friendlyName // .friendlyName // .friendly_name // .name)
    }
  | select(.id != null)
  | "\(.id) | \(.class) | \(.name)"'

echo "[6/6] Example: show first device keys and id/class"
echo "$DEVICES" | jq '.[0] | {keys: (keys)}' 2>/dev/null || true
echo "$DEVICES" | jq '.[0] | {
  id: (.deviceId // .id // .metadeviceId // .device_id),
  class: (.description.device.deviceClass // .device_class // .typeId // .description.deviceClass // .type)
}' 2>/dev/null || true

echo "Done. Temp files in $WORKDIR"
