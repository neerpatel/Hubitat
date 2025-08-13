# Authentication Endpoints

This document outlines the authentication endpoints used by the `aioafero` library.

## Hubspace Client

The following endpoints are used when the `afero_client` is set to `hubspace`.

### 1. Initial Authentication Request

*   **Method:** `GET`
*   **URL:** `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth`
*   **Description:** This is the initial request to begin the authentication process. It returns a login page or a redirect if there is an active session.

### 2. Generate Code

*   **Method:** `POST`
*   **URL:** `https://accounts.hubspaceconnect.com/auth/realms/thd/login-actions/authenticate`
*   **Description:** This request is sent after the user authenticates on the login page. It returns a code that is used to generate an access token.

### 3. Generate/Refresh Token

*   **Method:** `POST`
*   **URL:** `https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token`
*   **Description:** This request is used to generate a new access token and refresh token, or to refresh an expired access token using a refresh token.