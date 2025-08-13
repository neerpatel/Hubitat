# AferoBridgeV1 and Device State Update Sequence Diagram

This document details the core request handling and device state update flow within the `aioafero` library, focusing on the `AferoBridgeV1` and `BaseResourcesController` classes.

## Sequence Diagram: Device State Update

```mermaid
sequenceDiagram
    participant Client (e.g., Light Controller)
    participant BaseResourcesController
    participant AferoBridgeV1
    participant AferoAuth
    participant Afero API

    Client->>BaseResourcesController: Calls update(device_id, states)
    BaseResourcesController->>BaseResourcesController: Prepares payload (metadeviceId, values)
    BaseResourcesController->>AferoBridgeV1: Calls request("put", url, json=payload, headers=headers)
    AferoBridgeV1->>AferoAuth: Calls token(web_session)
    AferoAuth-->>AferoBridgeV1: Returns access_token
    AferoBridgeV1->>Afero API: PUT /v1/accounts/{account_id}/metadevices/{device_id}/state (Authorization: Bearer <access_token>, payload)
    Afero API-->>AferoBridgeV1: Response (200 OK or error)
    AferoBridgeV1-->>BaseResourcesController: Request result
    BaseResourcesController-->>Client: Update result (True/False)
```

## URLs Involved in Device State Updates and Data Fetching

### Device State Update

*   **URL Pattern:**
    *   For `hubspace` client: `https://api2.afero.net/v1/accounts/{account_id}/metadevices/{device_id}/state`
    *   For `myko` client: `https://api2.sxz2xlhh.afero.net/v1/accounts/{account_id}/metadevices/{device_id}/state`
*   **Method:** `PUT`
*   **Purpose:** To update the state of a specific Afero device. The `{account_id}` and `{device_id}` are placeholders that are dynamically inserted at runtime.

### Get Account ID

*   **URL Pattern:**
    *   For `hubspace` client: `https://api2.afero.net/v1/users/me`
    *   For `myko` client: `https://api2.sxz2xlhh.afero.net/v1/users/me`
*   **Method:** `GET`
*   **Purpose:** To retrieve the user's Afero account ID. This ID is then used in other API calls.

### Fetch All Device Data

*   **URL Pattern:**
    *   For `hubspace` client: `https://api2.afero.net/v1/accounts/{account_id}/metadevices`
    *   For `myko` client: `https://api2.sxz2xlhh.afero.net/v1/accounts/{account_id}/metadevices`
*   **Method:** `GET`
*   **Purpose:** To retrieve a list of all metadevices associated with the account, including their current states. The `{account_id}` is a placeholder dynamically inserted at runtime.
