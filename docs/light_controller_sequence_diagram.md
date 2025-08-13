# Light Controller Sequence Diagram

This document details the interaction flow for controlling light devices within the `aioafero` library, specifically focusing on the `LightController`.

## Sequence Diagram: Light Control (e.g., Turning On a Light)

```mermaid
sequenceDiagram
    participant User
    participant LightController
    participant BaseResourcesController
    participant AferoBridgeV1
    participant AferoAuth
    participant Afero API

    User->>LightController: Calls turn_on(device_id)
    LightController->>LightController: Calls set_state(device_id, on=True)
    LightController->>LightController: Constructs LightPut object with "on" state
    LightController->>BaseResourcesController: Calls update(device_id, obj_in=LightPut_object)
    BaseResourcesController->>BaseResourcesController: Converts LightPut object to Afero API states payload
    BaseResourcesController->>AferoBridgeV1: Calls request("put", url, json=payload, headers=headers)
    AferoBridgeV1->>AferoAuth: Calls token(web_session)
    AferoAuth-->>AferoBridgeV1: Returns access_token
    AferoBridgeV1->>Afero API: PUT /v1/accounts/{account_id}/metadevices/{device_id}/state (Authorization: Bearer <access_token>, payload: {"metadeviceId": "...", "values": [{"functionClass": "power", "value": "on", ...}]})
    Afero API-->>AferoBridgeV1: Response (200 OK or error)
    AferoBridgeV1-->>BaseResourcesController: Request result
    BaseResourcesController-->>LightController: Update result
    LightController-->>User: Light turned on (or error)
```

## URLs Involved in Light Control

*   **URL Pattern:**
    *   For `hubspace` client: `https://api2.afero.net/v1/accounts/{account_id}/metadevices/{device_id}/state`
    *   For `myko` client: `https://api2.sxz2xlhh.afero.net/v1/accounts/{account_id}/metadevices/{device_id}/state`
*   **Method:** `PUT`
*   **Purpose:** To update the state of a specific light device (e.g., turn on/off, set brightness, color, etc.). The `{account_id}` and `{device_id}` are placeholders that are dynamically inserted at runtime. The payload will contain specific `functionClass` and `value` pairs relevant to light attributes (e.g., `"functionClass": "power", "value": "on"` for turning on, `"functionClass": "brightness", "value": 50` for setting brightness).
