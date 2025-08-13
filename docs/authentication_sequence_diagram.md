# Authentication Sequence Diagram

This document details the authentication flow for the Afero API, as implemented in the `aioafero` library.

## Sequence Diagram

```mermaid
sequenceDiagram
    participant User
    participant AferoAuth (Client)
    participant Afero Webapp
    participant Afero API

    User->>AferoAuth (Client): Calls login(username, password)
    AferoAuth (Client)->>Afero Webapp: POST /login (username, password)
    Afero Webapp-->>AferoAuth (Client): HTML Response (contains login_qs)
    AferoAuth (Client)->>AferoAuth (Client): Extracts login_qs from HTML
    AferoAuth (Client)->>Afero API: POST /login (grant_type, username, password, login_qs)
    Afero API-->>AferoAuth (Client): Access Token, Refresh Token, Expiry

    alt Token Refresh
        AferoAuth (Client)->>Afero API: POST /login (grant_type=refresh_token, refresh_token)
        Afero API-->>AferoAuth (Client): New Access Token, New Refresh Token, New Expiry
    end
```

## URLs Involved in Authentication

*   **Web App Login:**
    *   **URL:** `https://webapp.afero.io/login`
    *   **Method:** `POST`
    *   **Purpose:** Initial login to the Afero web application to obtain a `login_qs` (query string) which is then used for API authentication.

*   **API Login and Token Refresh:**
    *   **URL:** `https://api.afero.io/v1/login`
    *   **Method:** `POST`
    *   **Purpose:**
        *   **Initial API Login:** Exchanges username, password, and the `login_qs` (obtained from web app login) for an `access_token` and `refresh_token`.
        *   **Token Refresh:** Uses the `refresh_token` to obtain a new `access_token` when the current one expires.
