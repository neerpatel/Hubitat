#!/usr/bin/env bash

set -euo pipefail

# Collect sample payloads for the Afero/HubSpace APIs used by aioafero.
# Saves results under ./research/
#
## Usage:
#   HUBSPACE_USERNAME="you@example.com" HUBSPACE_PASSWORD="secret" \
#     ./scripts/collect_afero_samples.sh
#
# Optional env vars:
#   AFERO_CLIENT=hubspace|myko   (default: hubspace)
#   WRITE_EXAMPLE=1              (also write a sample PUT payload template)

AFERO_CLIENT="${AFERO_CLIENT:-hubspace}"
WRITE_EXAMPLE="${WRITE_EXAMPLE:-0}"

case "$AFERO_CLIENT" in
  hubspace)
    AUTH_HOST="accounts.hubspaceconnect.com"
    AUTH_REALM="thd"
    CLIENT_ID="hubspace_android"
    REDIRECT_URI="hubspace-app://loginredirect"
    API_HOST="api2.afero.net"
    DATA_HOST="semantics2.afero.net"
    ;;
  myko)
    AUTH_HOST="accounts.mykoapp.com"
    AUTH_REALM="kfi"
    CLIENT_ID="kfi_android"
    REDIRECT_URI="kfi-app://loginredirect"
    API_HOST="api2.sxz2xlhh.afero.net"
    DATA_HOST="semantics2.sxz2xlhh.afero.net"
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

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required for redaction/parsing" >&2; exit 1; }

RESEARCH_DIR="$(pwd)/research"
mkdir -p "$RESEARCH_DIR"

USER_AGENT="Dart/3.1 (dart:io)"

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

CODE_VERIFIER=$(head -c 40 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=' | tr -cd '[:alnum:]')
CODE_CHALLENGE=$(printf "%s" "$CODE_VERIFIER" | openssl dgst -binary -sha256 | base64url)

echo "[1/7] GET OpenID login page"
AUTH_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/auth"
curl -sS -D "$HDR1" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -G "$AUTH_URL" \
  -H "User-Agent: $USER_AGENT" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "scope=openid offline_access" \
  --data-urlencode "code_challenge=$CODE_CHALLENGE" \
  --data-urlencode "code_challenge_method=S256" \
  -o "$BODY1"

# Save raw HTML and headers for reference
cp "$BODY1" "$RESEARCH_DIR/01_openid_login_page.html"
cp "$HDR1" "$RESEARCH_DIR/01_openid_login_headers.txt"

FORM_ACTION=$(grep -Eo 'action="[^"]*login-actions/authenticate[^"]*"' "$BODY1" | head -n1 | sed -E 's/^action="(.*)"$/\1/' | sed -e 's/&amp;/\&/g')
if [[ -z "$FORM_ACTION" ]]; then
  # Fallback: parse hidden inputs
  SESSION_CODE=$(grep -Eo 'name="session_code" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  EXECUTION=$(grep -Eo 'name="execution" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  TAB_ID=$(grep -Eo 'name="tab_id" value="[^"]*"' "$BODY1" | head -n1 | sed -E 's/.*value="([^"]*)".*/\1/')
  if [[ -z "$SESSION_CODE" || -z "$EXECUTION" || -z "$TAB_ID" ]]; then
    echo "Could not parse login form/action; aborting." >&2
    exit 1
  fi
  FORM_ACTION="https://$AUTH_HOST/auth/realms/$AUTH_REALM/login-actions/authenticate?session_code=$SESSION_CODE&execution=$EXECUTION&client_id=$CLIENT_ID&tab_id=$TAB_ID"
fi

echo "[2/7] POST login (expect 302 with code)"
curl -sS -D "$HDR2" -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -X POST "$FORM_ACTION" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" \
  --data-urlencode "credentialId=" \
  -o "$BODY2"

# Save login response headers (Location carries ?code=...)
cp "$HDR2" "$RESEARCH_DIR/02_login_response_headers.txt"

LOCATION=$(grep -i '^Location:' "$HDR2" | awk '{ $1=""; sub(/^ /,""); print }' | tr -d '\r\n')
AUTH_CODE=$(echo "$LOCATION" | sed -n 's/.*[?&]code=\([^&]*\).*/\1/p')
if [[ -z "$AUTH_CODE" ]]; then
  echo "No authorization code found after login." >&2
  exit 1
fi

echo "[3/7] Exchange code for tokens (redacted on disk)"
TOKEN_URL="https://$AUTH_HOST/auth/realms/$AUTH_REALM/protocol/openid-connect/token"
TOKEN_JSON=$(curl -sS -X POST "$TOKEN_URL" \
  -H "User-Agent: $USER_AGENT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Accept: application/json" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "redirect_uri=$REDIRECT_URI" \
  --data-urlencode "code=$AUTH_CODE" \
  --data-urlencode "code_verifier=$CODE_VERIFIER")

# Redact tokens before writing
echo "$TOKEN_JSON" | jq '{
  access_token: (if .access_token then ("REDACTED_" + ((.access_token|tostring|length)|tostring)) else null end),
  refresh_token: (if .refresh_token then ("REDACTED_" + ((.refresh_token|tostring|length)|tostring)) else null end),
  id_token: (if .id_token then ("REDACTED_" + ((.id_token|tostring|length)|tostring)) else null end),
  expires_in, token_type, scope
}' > "$RESEARCH_DIR/03_token_response_redacted.json"

ACCESS_TOKEN=$(echo "$TOKEN_JSON" | jq -r '.access_token')
if [[ -z "$ACCESS_TOKEN" || "$ACCESS_TOKEN" == "null" ]]; then
  echo "Failed to obtain access_token." >&2
  echo "$TOKEN_JSON" > "$RESEARCH_DIR/03_token_response_raw_error.json"
  exit 1
fi

echo "[4/7] GET users/me (redacted)"
ME_JSON=$(curl -sS -X GET "https://$API_HOST/v1/users/me" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Host: $API_HOST")

# Redact potential PII while keeping structure
echo "$ME_JSON" | jq '{
  accountAccess: (.accountAccess | map({account: {accountId: .account.accountId}})),
  user: ({ id: (.user.id // null) } // {}),
  _note: "Sensitive fields removed"
}' > "$RESEARCH_DIR/04_users_me_redacted.json"

ACCOUNT_ID=$(echo "$ME_JSON" | jq -r '.accountAccess[0].account.accountId')
if [[ -z "$ACCOUNT_ID" || "$ACCOUNT_ID" == "null" ]]; then
  echo "Could not extract accountId from users/me." >&2
  exit 1
fi

echo "[5/7] GET ƒdevices?expansions=state"
curl -sS -X GET "https://$API_HOST/v1/accounts/$ACCOUNT_ID/metadevices?expansions=state" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Host: $DATA_HOST" \
  -o "$RESEARCH_DIR/05_metadevices_with_state.json"

# Compact listing for quick scan
jq -r '.[] | {id: (.deviceId // .id // .metadeviceId), class: (.description.device.deviceClass // .typeId), name: (.description.device.friendlyName // .friendlyName // .name)} | select(.id!=null) | "\(.id) | \(.class) | \(.name)"' \
  "$RESEARCH_DIR/05_metadevices_with_state.json" > "$RESEARCH_DIR/05b_metadevices_compact.txt" || true

# Pick the first real metadevice id
FIRST_ID=$(jq -r 'map(select(.typeId=="metadevice.device" and (.deviceId!=null))) | .[0].deviceId' "$RESEARCH_DIR/05_metadevices_with_state.json" 2>/dev/null || true)

if [[ -n "$FIRST_ID" && "$FIRST_ID" != "null" ]]; then
  echo "[6/7] GET device versions for first device ($FIRST_ID)"
  curl -sS -X GET "https://$API_HOST/v1/accounts/$ACCOUNT_ID/devices/$FIRST_ID/versions" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Host: $API_HOST" \
    -o "$RESEARCH_DIR/06_first_device_versions.json" || true
else
  echo "[6/7] Skipping versions: no metadevices found"
fi

if [[ "$WRITE_EXAMPLE" == "1" ]]; then
  echo "[7/7] Generate PUT state template (no write)"
  # Derive a simple example: toggle power if present; otherwise echo a placeholder
  if [[ -n "${FIRST_ID:-}" && "$FIRST_ID" != "null" ]]; then
    jq --arg dev "$FIRST_ID" '
      .[] | select(.typeId=="metadevice.device" and .deviceId==$dev) | . as $d |
      ($d.state // $d.states // []) as $st |
      ( $st | map(select(.functionClass=="power")) | .[0] ) as $p |
      if $p != null then
        { metadeviceId: $dev, values: [ { functionClass: "power", value: ( ($p.value|tostring|ascii_downcase) as $v | if $v=="on" or $v=="true" then "off" else "on" end ) } ] }
      else
        { metadeviceId: $dev, values: [ { functionClass: "brightness", value: 50 } ] }
      end
    ' "$RESEARCH_DIR/05_metadevices_with_state.json" > "$RESEARCH_DIR/07_put_state_template.json" || true
  else
    echo '{"metadeviceId":"<deviceId>","values":[{"functionClass":"power","value":"on"}]}' > "$RESEARCH_DIR/07_put_state_template.json"
  fi
else
  echo "[7/7] Skipping PUT template (set WRITE_EXAMPLE=1 to generate)"
fi

cat > "$RESEARCH_DIR/_manifest.txt" <<EOF
Collected Afero/HubSpace API samples (client: $AFERO_CLIENT)

01_openid_login_page.html         - Initial OpenID auth HTML (PKCE flow)
01_openid_login_headers.txt       - Response headers for OpenID GET (may include cookies)
02_login_response_headers.txt     - Login POST response headers (Location has ?code=...)
03_token_response_redacted.json   - Token exchange (authorization_code) with sensitive fields redacted
04_users_me_redacted.json         - GET /v1/users/me (accountId retained; PII redacted)
05_metadevices_with_state.json    - GET /v1/accounts/{accountId}/metadevices?expansions=state
05b_metadevices_compact.txt       - id | class | name summary extracted from 05
06_first_device_versions.json     - GET /v1/accounts/{accountId}/devices/{deviceId}/versions (first device)
07_put_state_template.json        - Example payload to PUT metadevice state (only when WRITE_EXAMPLE=1)
EOF

echo "Done. Samples written to $RESEARCH_DIR"

