# Sequence Diagrams

This document contains sequence diagrams that illustrate the interaction between the `aiofero` library and the Afero API.

## Authentication

### Initial Login

```mermaid
sequenceDiagram
    participant Client
    participant AferoAuth
    participant Afero API

    Client->>AferoAuth: token()
    AferoAuth->>AferoAuth: perform_initial_login()
    AferoAuth->>AferoAuth: generate_challenge_data()
    AferoAuth->>Afero API: GET /auth/realms/thd/protocol/openid-connect/auth (webapp_login)
    Afero API-->>AferoAuth: Login Page HTML
    AferoAuth->>AferoAuth: extract_login_data()
    AferoAuth->>Afero API: POST /auth/realms/thd/login-actions/authenticate (generate_code)
    Afero API-->>AferoAuth: Redirect with code
    AferoAuth->>AferoAuth: parse_code()
    AferoAuth->>Afero API: POST /auth/realms/thd/protocol/openid-connect/token (generate_refresh_token)
    Afero API-->>AferoAuth: TokenData (access_token, refresh_token, etc.)
    AferoAuth-->>Client: token
```

### Refresh Token

```mermaid
sequenceDiagram
    participant Client
    participant AferoAuth
    participant Afero API

    Client->>AferoAuth: token()
    AferoAuth->>AferoAuth: is_expired()
    alt Token is expired
        AferoAuth->>Afero API: POST /auth/realms/thd/protocol/openid-connect/token (generate_refresh_token)
        Afero API-->>AferoAuth: New TokenData
    end
    AferoAuth-->>Client: token
```

## Device Control

```mermaid
sequenceDiagram
    participant Client
    participant DeviceController
    participant BaseResourcesController
    participant AferoBridgeV1
    participant Afero API

    Client->>DeviceController: set_state(device_id, ...)
    DeviceController->>BaseResourcesController: update(device_id, obj_in=update_obj)
    BaseResourcesController->>BaseResourcesController: dataclass_to_afero(...)
    BaseResourcesController->>BaseResourcesController: update_afero_api(device_id, device_states)
    BaseResourcesController->>AferoBridgeV1: request("put", url, json=payload, headers=headers)
    AferoBridgeV1->>AferoBridgeV1: create_request(...)
    AferoBridgeV1->>Afero API: PUT /v1/accounts/{account_id}/metadevices/{device_id}/state
    Afero API-->>AferoBridgeV1: Response
    AferoBridgeV1-->>BaseResourcesController: Response
    BaseResourcesController-->>DeviceController: 
    DeviceController-->>Client:
```
