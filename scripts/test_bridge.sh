#!/usr/bin/env bash

set -euo pipefail

# Test the Node.js HubSpace bridge
# Usage:
#   HUBSPACE_USERNAME="email" HUBSPACE_PASSWORD="pass" ./scripts/test_bridge.sh
# Optional:
#   BRIDGE_URL=http://localhost:3000 (default)
#   TEST_COMMANDS=onoff (send power on/off to first light/switch) — use with caution

BRIDGE_URL="${BRIDGE_URL:-http://localhost:3000}"
USERNAME="${HUBSPACE_USERNAME:-}"
PASSWORD="${HUBSPACE_PASSWORD:-}"
TEST_COMMANDS="${TEST_COMMANDS:-}" # empty by default (read-only)

if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Set HUBSPACE_USERNAME and HUBSPACE_PASSWORD env vars." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
echo "Workdir: $WORKDIR"

echo "[1/6] Bridge health"
curl -sS "$BRIDGE_URL/health" | tee "$WORKDIR/health.json" | jq '.' 2>/dev/null || true

echo "[2/6] Login via bridge"
LOGIN_JSON=$(curl -sS -X POST "$BRIDGE_URL/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
echo "$LOGIN_JSON" | jq '.' 2>/dev/null || true
SESSION=$(echo "$LOGIN_JSON" | jq -r '.sessionId' 2>/dev/null || true)
ACCOUNT=$(echo "$LOGIN_JSON" | jq -r '.accountId' 2>/dev/null || true)
if [[ -z "$SESSION" || "$SESSION" == "null" ]]; then
  echo "Bridge login failed: $LOGIN_JSON" >&2
  exit 1
fi
echo "Session: $SESSION Account: $ACCOUNT"

echo "[3/6] List devices"
DEVICES=$(curl -sS "$BRIDGE_URL/devices?session=$SESSION")
COUNT=$(echo "$DEVICES" | jq 'length' 2>/dev/null || echo "n/a")
echo "Devices returned: $COUNT"
echo "$DEVICES" | jq -r '.[] | "\(.id) | \(.typeId) | \(.device_class) | \(.friendlyName)"' 2>/dev/null || true

echo "[4/6] Pick first metadevice.device"
FIRST_ID=$(echo "$DEVICES" | jq -r 'map(select(.typeId=="metadevice.device")) | .[0].id' 2>/dev/null || true)
FIRST_CLASS=$(echo "$DEVICES" | jq -r 'map(select(.typeId=="metadevice.device")) | .[0].device_class' 2>/dev/null || true)
if [[ -z "$FIRST_ID" || "$FIRST_ID" == "null" ]]; then
  echo "No metadevice.device found; picking first entry"
  FIRST_ID=$(echo "$DEVICES" | jq -r '.[0].id' 2>/dev/null || true)
  FIRST_CLASS=$(echo "$DEVICES" | jq -r '.[0].device_class' 2>/dev/null || true)
fi
echo "Device: $FIRST_ID class: $FIRST_CLASS"

echo "[5/6] Get device state"
curl -sS "$BRIDGE_URL/state/$FIRST_ID?session=$SESSION" | tee "$WORKDIR/state.json" | jq '.' 2>/dev/null || true

if [[ "$TEST_COMMANDS" == "onoff" ]]; then
  echo "[6/6] Send power on/off (dangerous; modifies a device)"
  if [[ "$FIRST_CLASS" == "light" || "$FIRST_CLASS" == "switch" ]]; then
    echo "Turning device ON"
    curl -sS -X POST "$BRIDGE_URL/command/$FIRST_ID?session=$SESSION" \
      -H 'Content-Type: application/json' \
      -d '{"values":[{"functionClass":"power","value":"on"}]}' | jq '.' 2>/dev/null || true
    sleep 1
    echo "Turning device OFF"
    curl -sS -X POST "$BRIDGE_URL/command/$FIRST_ID?session=$SESSION" \
      -H 'Content-Type: application/json' \
      -d '{"values":[{"functionClass":"power","value":"off"}]}' | jq '.' 2>/dev/null || true
  else
    echo "Skipping ON/OFF test; device_class=$FIRST_CLASS"
  fi
else
  echo "[6/6] Read-only test complete (set TEST_COMMANDS=onoff to toggle a device)"
fi

echo "Done."

