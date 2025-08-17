# HubSpace Home Assistant Integration: Architecture and API Flows

This document explains how the module at `tmp/Hubspace-Homeassistant` integrates HubSpace devices with Home Assistant using the `tmp/aioafero` library. It details authentication, token storage, polling, device mapping, and command flows with concrete endpoints and payloads.

## Components Overview

- `custom_components/hubspace/bridge.py`: Orchestrates login, initializes `aioafero` bridge (`AferoBridgeV1`), subscribes to events, and forwards HA platforms.
- `custom_components/hubspace/device.py`: Registers devices in HA’s Device Registry and wires controller events to HA devices.
- `custom_components/hubspace/entity.py`: Base entity wiring to controller events; provides `update_decorator` to eagerly reflect state after commands.
- Platform entities (e.g., `light.py`, `fan.py`, `lock.py`, `climate.py`, `switch.py`, `valve.py`): Map HA capabilities to `aioafero` controller methods (e.g., `LightController.set_state`).
- `tmp/aioafero`: Client library encapsulating HubSpace (Afero IoT) v1 API: auth (PKCE + refresh), polling, resource models, and controllers for each device type.

## Authentication and Token Management

`aioafero.v1.auth.AferoAuth` implements PKCE + refresh-token flow against HubSpace Keycloak (realm `thd`). Tokens are managed in-memory in `TokenData` and automatically refreshed.

### Key Endpoints (Auth)

- GET `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth`
  - Query params: `response_type=code`, `client_id=hubspace_android`, `redirect_uri=hubspace-app://loginredirect`, `code_challenge=<S256>`, `code_challenge_method=S256`, `scope=openid offline_access`.
  - Purpose: Obtain the login form (or an immediate redirect) to start the PKCE flow.

- POST `https://accounts.hubspaceconnect.com/auth/realms/thd/login-actions/authenticate`
  - Query params: `session_code`, `execution`, `client_id=hubspace_android`, `tab_id` (extracted from the login form action).
  - Body (form): `username`, `password`, `credentialId`.
  - On success returns 302 redirect whose `Location` contains `?code=...`.

- POST `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token`
  - Body (form):
    - Authorization Code Grant: `grant_type=authorization_code`, `code`, `redirect_uri=hubspace-app://loginredirect`, `code_verifier`, `client_id=hubspace_android`.
    - Refresh Token Grant: `grant_type=refresh_token`, `refresh_token`, `scope=openid email offline_access profile`, `client_id=hubspace_android`.
  - Response (JSON): `{ access_token, refresh_token, id_token, expires_in, ... }`.

### How Tokens Are Stored and Used

- `AferoBridgeV1` is created with `username`, `password`, and an optional `refresh_token` (e.g., from a prior session or config entry). Internally it owns an `AferoAuth` instance.
- `AferoAuth.token(session)` returns a short-lived ID token, auto-refreshing based on `expires_in`. It refreshes using the stored `refresh_token`, and if that fails, it performs the full PKCE login again.
- The `Authorization: Bearer <token>` header is injected by `AferoBridgeV1.create_request(...)` for all API requests.

## Afero API Usage (HubSpace Cloud)

`AferoBridgeV1` targets the HubSpace Afero endpoints:

- Hosts: `api2.afero.net` (core), `semantics2.afero.net` (data)
- Account discovery: `GET https://api2.afero.net/v1/users/me`
  - Headers: `Authorization: Bearer <token>`, `host: api2.afero.net`
  - Response: `{ accountAccess: [{ account: { accountId: "..."}}], ... }`

- Device list (with states): `GET https://api2.afero.net/v1/accounts/{accountId}/metadevices?expansions=state`
  - Headers: `Authorization: Bearer <token>`, `host: semantics2.afero.net`
  - Response: Array of meta-devices; each item includes `description.device.deviceClass`, `deviceId`, `typeId`, and `state` when expanded.

- Device firmware versions (optional polling): `GET https://api2.afero.net/v1/accounts/{accountId}/devices/{deviceId}/versions`
  - Headers: `Authorization: Bearer <token>`
  - Response: Version metadata for given physical device.

- Set device state: `PUT https://api2.afero.net/v1/accounts/{accountId}/metadevices/{deviceId}/state`
  - Headers: `Authorization: Bearer <token>`, `host: semantics2.afero.net`, `content-type: application/json; charset=utf-8`
  - Payload shape:
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

## Eventing and Polling Flow

- `AferoBridgeV1.initialize()`
  1) Resolves `accountId` via `/v1/users/me`.
  2) Initializes device-specific controllers.
  3) Starts the `EventStream` poller at configured `polling_interval` seconds.

- `EventStream` loop
  - Calls `AferoBridgeV1.fetch_data(version_poll=...)` → GET metadevices (+ optional versions)
  - Emits:
    - `POLLED_DATA`: raw payload
    - `POLLED_DEVICES`: parsed devices (split if needed)
    - `RESOURCE_ADDED` / `RESOURCE_UPDATED` / `RESOURCE_DELETED` per device
  - Handles HTTP errors with exponential-ish backoff and status events: `CONNECTED`, `DISCONNECTED`, `RECONNECTED`, `INVALID_AUTH`.

## Device Mapping and Controllers

Controllers (e.g., `LightController`, `SwitchController`, `FanController`, `LockController`, `ThermostatController`, `ValveController`) map HubSpace function classes to rich models with features. Examples:

- Light (`light.py` in HA):
  - Read: `.resource.is_on`, `.brightness`, `.color_temperature`, `.color`, `.effect`.
  - Write: `controller.set_state(device_id=..., on=True/False, brightness=1..100, temperature=Kelvin, color=(r,g,b), color_mode="white|color|sequence", effect="..." )` → translates to PUT with `functionClass` values like `power`, `brightness`, `color-temperature`, `color-rgb`, etc.

- Switch: `power` on/off.

- Fan: `fan-speed` (numeric or keywords), `fan-direction`.

- Lock: `lock` values `locked`/`unlocked`.

- Thermostat:
  - Read: `temperature` instances (`current-temp`, `heating-target`, `cooling-target`, `auto-...`, `safety-mode-...`), `mode`, `fan-mode`, `current-system-state`.
  - Write: `temperature` with appropriate `functionInstance`, `mode`, `fan-mode`.

- Valve/Water Timer: `power` on/off, `timer-duration`.

`BaseResourcesController.update_afero_api(...)` centralizes PUT formation; features and models expose `.api_value` so lists or objects are correctly shaped in the payload.

## Home Assistant Glue

- `HubspaceBridge` (`bridge.py`):
  - Creates `AferoBridgeV1(username, password, refresh_token, session, polling_interval)`
  - `await api.initialize()` then `async_setup_devices(self)` and `forward_entry_setups(...)` to load platforms.
  - Subscribes to `EventType.INVALID_AUTH` to trigger HA reauth flow.
  - Provides `async_request_call` wrapper to safely call controller methods from entities.

- `device.py`: Registers devices in Device Registry using Wi-Fi/BLE MACs and `parent_id` as identifier.

- `entity.py`: Base class that auto-subscribes to `RESOURCE_UPDATED` signals and uses an `update_decorator` so that commands reflect state immediately.

## End-to-End Sequences

### 1) Initial Login + Bootstrap

1. HA config flow collects `username`, `password` (optional `refresh_token`).
2. `HubspaceBridge` constructs `AferoBridgeV1`.
3. `AferoBridgeV1.initialize()` → `AferoAuth.token()` performs PKCE:
   - GET OpenID login page (Keycloak) to extract `session_code`, `execution`, `tab_id`.
   - POST login form to `/login-actions/authenticate` to receive `code` via redirect.
   - POST `/protocol/openid-connect/token` to receive `{access_token, refresh_token, id_token}`.
   - Cache `TokenData` with `expiration=now+TOKEN_TIMEOUT`.
4. GET `/v1/users/me` (api2.afero.net) to fetch `accountId`.
5. Poll GET `/v1/accounts/{accountId}/metadevices?expansions=state` (semantics2.afero.net).
6. Emit add/update events and register HA entities.

### 2) Refresh Token

1. Timer detects token near-expiry.
2. POST `/protocol/openid-connect/token` with `grant_type=refresh_token`.
3. On `invalid_grant`, throw `InvalidAuth` → Bridge emits `INVALID_AUTH` → HA reauth flow.

### 3) Command Dispatch (Example: Light setColor)

1. HA calls `HubspaceLight.async_turn_on(ATTR_RGB_COLOR=(r,g,b))`.
2. Entity calls `await controller.set_state(device_id, on=True, color=(r,g,b), color_mode="color")`.
3. Controller builds states: `[ {"functionClass": "power", "value":"on"}, {"functionClass":"color-rgb", "value":{"r":r,"g":g,"b":b}} ]`.
4. PUT `/v1/accounts/{accountId}/metadevices/{deviceId}/state` with payload.
5. On success, entity triggers `RESOURCE_UPDATED` via `update_decorator` and the next poll confirms.

## Headers, Hosts, and Notes

- Always send `Authorization: Bearer <token>`.
- Data host header varies by endpoint (core: `api2.afero.net`, data: `semantics2.afero.net`). `AferoBridgeV1.request()` sets `ssl=True` and proper `user-agent`.
- Controllers enforce retries on 429/503 with short backoff; `ExceededMaximumRetries` surfaces when API is persistently unavailable.

## Relation to Hubitat App/Drivers

The Hubitat app mirrors the same API flows:
- PKCE auth with HubSpace Keycloak → stores `state.accessToken`, `state.refreshToken`, `state.tokenExpires`.
- GET `/v1/users/me` for `accountId`.
- GET `/v1/accounts/{accountId}/metadevices?expansions=state` to discover/create child devices.
- PUT `/v1/accounts/{accountId}/metadevices/{deviceId}/state` from drivers via `parent.sendHsCommand(...)` with `functionClass`, optional `functionInstance`, and `value`.

See `Hubitat/app/Hubspace/HubspaceDeviceManager.groovy` for the end-to-end reference implementation in Hubitat.

