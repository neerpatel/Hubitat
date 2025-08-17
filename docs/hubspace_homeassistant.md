# HubSpace Home Assistant Module Overview

## Authentication Flow
1. **Initialize `AferoBridgeV1`** – The Home Assistant integration constructs an `AferoBridgeV1` instance with the user credentials and a stored refresh token, allowing reuse of previous sessions【F:tmp/Hubspace-Homeassistant/custom_components/hubspace/bridge.py†L47-L66】
2. **PKCE Challenge** – `AferoAuth.generate_challenge_data` creates a verifier and challenge used for the OAuth login sequence with HubSpace servers【F:tmp/aioafero/src/aioafero/v1/auth.py†L178-L188】
3. **Authorization Request** – `AferoAuth.webapp_login` sends a GET request to `/protocol/openid-connect/auth` with client ID `hubspace_android` and the PKCE challenge to obtain session parameters【F:tmp/aioafero/src/aioafero/v1/auth.py†L120-L154】【F:tmp/aioafero/src/aioafero/v1/v1_const.py†L33-L35】
4. **Credential Submission** – `AferoAuth.generate_code` posts the username and password to `/login-actions/authenticate` to retrieve an authorization code【F:tmp/aioafero/src/aioafero/v1/auth.py†L190-L239】
5. **Token Exchange** – `AferoAuth.generate_refresh_token` exchanges the code for tokens by POSTing to `/protocol/openid-connect/token` and stores `refresh_token`, `access_token`, and `id_token` along with expiration data【F:tmp/aioafero/src/aioafero/v1/auth.py†L255-L334】
6. **Token Refresh** – `AferoAuth.token` refreshes tokens when expired or missing, reusing the stored refresh token【F:tmp/aioafero/src/aioafero/v1/auth.py†L352-L378】
7. **Account Lookup** – `AferoBridgeV1.get_account_id` queries `/v1/users/me` to obtain the HubSpace account identifier used in subsequent API requests【F:tmp/aioafero/src/aioafero/v1/__init__.py†L305-L323】【F:tmp/aioafero/src/aioafero/v1/v1_const.py†L36】
8. **Persistent Storage** – During configuration, the integration saves the refresh token in the Home Assistant config entry (`CONF_TOKEN`) so future sessions can reuse it without re-authentication【F:tmp/Hubspace-Homeassistant/custom_components/hubspace/config_flow.py†L134-L137】

## Device Management
1. **Bridge Initialization** – `HubspaceBridge.async_initialize_bridge` calls `AferoBridgeV1.initialize` to populate controllers and start event processing【F:tmp/Hubspace-Homeassistant/custom_components/hubspace/bridge.py†L68-L115】
2. **Device Registry** – Devices discovered from the bridge are registered in Home Assistant’s device registry for tracking and later access【F:tmp/Hubspace-Homeassistant/custom_components/hubspace/device.py†L18-L53】
3. **Polling and Events** – The bridge schedules periodic polling via the `EventStream` in `AferoBridgeV1` while also listening for invalid authentication events to trigger re-authentication【F:tmp/Hubspace-Homeassistant/custom_components/hubspace/bridge.py†L103-L113】

## Device Control
1. **Controller Methods** – Each device type exposes high level actions (e.g., `turn_on`, `set_speed`) implemented by corresponding aioafero controllers such as `LightController` or `FanController`.
2. **State Update Request** – Controller `set_state` methods translate feature objects into raw state dictionaries before calling `BaseResourcesController.update`【F:tmp/aioafero/src/aioafero/v1/controllers/fan.py†L169-L176】【F:tmp/aioafero/src/aioafero/v1/controllers/base.py†L468-L507】
3. **HTTP PUT to Device** – `update_afero_api` sends a PUT request to `/v1/accounts/{accountId}/metadevices/{deviceId}/state` with payload `{"metadeviceId": "<id>", "values": [...]}` to apply the state changes【F:tmp/aioafero/src/aioafero/v1/controllers/base.py†L436-L456】

## Endpoints Summary
| Purpose | Endpoint |
|---------|----------|
| Authorization page | `/protocol/openid-connect/auth` |
| Submit credentials | `/login-actions/authenticate` |
| Token exchange/refresh | `/protocol/openid-connect/token` |
| Account info | `/v1/users/me` |
| List devices | `/v1/accounts/{accountId}/metadevices` |
| Update device state | `/v1/accounts/{accountId}/metadevices/{deviceId}/state` |
| Device version info | `/v1/accounts/{accountId}/devices/{deviceId}/versions` |

These calls rely on the host and client constants defined in `v1_const.py`, which provide the HubSpace-specific API and authentication endpoints【F:tmp/aioafero/src/aioafero/v1/v1_const.py†L5-L36】
