# HubSpace API Sequences and Payloads

This reference lists the key sequences, endpoints, request/response shapes, and headers used by the HubSpace integration (`tmp/Hubspace-Homeassistant`) and the `aioafero` client (`tmp/aioafero`). It complements `hubspace_homeassistant_integration.md` with more concrete I/O detail.

## Host Matrix

- Auth (OpenID/Keycloak): `accounts.hubspaceconnect.com`
- Core API: `api2.afero.net`
- Data API: `semantics2.afero.net`

Include `Authorization: Bearer <token>` for all API calls to `afero.net` hosts.

## 1) Authentication (PKCE + Refresh)

Step A: Generate PKCE data
- `code_verifier`: random URL-safe base64 string
- `code_challenge`: `BASE64URL(SHA256(code_verifier))`

Step B: GET OpenID login page
- GET `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth`
  - Params: `response_type=code`, `client_id=hubspace_android`, `redirect_uri=hubspace-app://loginredirect`, `code_challenge=<S256>`, `code_challenge_method=S256`, `scope=openid offline_access`
  - 200: HTML with form `#kc-form-login`, whose action includes `session_code`, `execution`, `tab_id`.
  - 302: Already logged in → `Location` contains `?code=...` (skip to Step D).

Step C: POST login form
- POST `https://accounts.hubspaceconnect.com/auth/realms/thd/login-actions/authenticate`
  - Query: `session_code`, `execution`, `client_id=hubspace_android`, `tab_id`
  - Body (form): `username`, `password`, `credentialId` (empty)
  - 302: `Location` contains `?code=...`

Step D: Exchange code for tokens
- POST `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token`
  - Body (form): `grant_type=authorization_code`, `code`, `redirect_uri=hubspace-app://loginredirect`, `code_verifier`, `client_id=hubspace_android`
  - 200 JSON:
    ```json
    {
      "access_token": "...",
      "refresh_token": "...",
      "id_token": "...",
      "expires_in": 120,
      ...
    }
    ```

Step E: Refresh on expiry
- POST same token endpoint with: `grant_type=refresh_token`, `refresh_token`, `client_id=hubspace_android`, `scope=openid email offline_access profile`
  - 200 JSON replaces tokens; `error=invalid_grant` requires full login.

## 2) Get Account ID

- GET `https://api2.afero.net/v1/users/me`
  - Headers: `Authorization`, `host: api2.afero.net`
  - 200 JSON:
    ```json
    {
      "accountAccess": [
        { "account": { "accountId": "<id>" }, ... }
      ],
      ...
    }
    ```

## 3) List Devices (with State)

- GET `https://api2.afero.net/v1/accounts/{accountId}/metadevices?expansions=state`
  - Headers: `Authorization`, `host: semantics2.afero.net`
  - 200 JSON: Array of meta-devices. Each item has (simplified):
    ```json
    {
      "deviceId": "<id>",
      "typeId": "metadevice.device",
      "description": { "device": { "deviceClass": "light|switch|fan|door-lock|...", ... }},
      "state": [
        {
          "functionClass": "power|brightness|color-temperature|...",
          "functionInstance": "<instance-or-null>",
          "value": "on|off|... or number or object",
          "lastUpdateTime": 1710000000
        }
      ]
    }
    ```

## 4) Get Device Versions (optional)

- GET `https://api2.afero.net/v1/accounts/{accountId}/devices/{deviceId}/versions`
  - Headers: `Authorization`
  - 200 JSON: Firmware/version information per device.

## 5) Update Device State (Commands)

- PUT `https://api2.afero.net/v1/accounts/{accountId}/metadevices/{deviceId}/state`
  - Headers: `Authorization`, `host: semantics2.afero.net`, `content-type: application/json; charset=utf-8`
  - Body:
    ```json
    {
      "metadeviceId": "<deviceId>",
      "values": [
        {
          "functionClass": "<class>",
          "functionInstance": "<instance-or-null>",
          "lastUpdateTime": 1710000000,
          "value": <scalar-or-object>
        }
      ]
    }
    ```

### Function Class Examples

- Light:
  - On/Off: `power = "on"|"off"`
  - Level: `brightness = 1..100`
  - CT: `color-temperature = Kelvin`
  - RGB: `color-rgb = { "r": 0..255, "g": 0..255, "b": 0..255 }`

- Switch/Outlet: `power = true|false` or `"on"|"off"` depending on device

- Fan:
  - Speed: `fan-speed = 0..5 | "auto"`
  - Direction: `fan-direction = "forward"|"reverse"`

- Lock: `lock = "locked"|"unlocked"`

- Thermostat:
  - Mode: `mode = "off"|"heat"|"cool"|"auto"|...`
  - Fan Mode: `fan-mode = "on"|"auto"|...`
  - Temperatures (Celsius by default):
    - `temperature@heating-target`
    - `temperature@cooling-target`
    - `temperature@auto-heating-target`
    - `temperature@auto-cooling-target`
    - Safety: `temperature@safety-mode-max-temp`, `temperature@safety-mode-min-temp`

- Valve/Water Timer:
  - On/Off: `power = "on"|"off"`
  - Timer: `timer-duration = minutes`

### Response Semantics

- 200: Accepted/OK. Payload may be empty; subsequent polling reflects state.
- 400: Invalid state combination → controllers log warning and return False.
- 403: Invalid/expired token → handled upstream; triggers `INVALID_AUTH`.
- 429/503: Retry with backoff; after 3 attempts yields `ExceededMaximumRetries`.

## 6) Eventing and Subscriptions

- `EventStream` polls and emits:
  - Connection: `CONNECTED`, `DISCONNECTED`, `RECONNECTED`, `INVALID_AUTH`
  - Data: `POLLED_DATA` (raw JSON), `POLLED_DEVICES` (parsed),
  - Per-device: `RESOURCE_ADDED`, `RESOURCE_UPDATED`, `RESOURCE_DELETED`

Subscribers can filter by event type and resource type. Controllers forward updates to HA entities or other consumers.

