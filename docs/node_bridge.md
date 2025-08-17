# Node.js HubSpace Bridge

A minimal Node.js service that performs HubSpace (Afero) authentication and proxies device listing, state retrieval, and commands. This offloads cookies/PKCE and token refresh complexities out of the Hubitat app.

## Endpoints

- POST `/login`
  - Body: `{ "username": "...", "password": "..." }`
  - Response: `{ "sessionId": "...", "accountId": "..." }`

- GET `/devices?session=<sessionId>`
  - Response: `[{ id, typeId, device_class, friendlyName, states? }, ...]`

- GET `/state/{id}?session=<sessionId>`
  - Response: `{ states: [...] }` (raw Afero response)

- POST `/command/{id}?session=<sessionId>`
  - Body: `{ values: [{ functionClass, functionInstance?, value }] }`
  - Response: `{ ok: true }` on success

## Install and Run

- `cd bridge-node`
- `npm install`
- `npm start`
- Default port: `3000` (override with `PORT` env var)

## Hubitat App Configuration

- Enable “Use Node.js Bridge” and set `Node Bridge URL` (e.g. `http://<bridge-ip>:3000`).
- Authenticate from the app; it will call `/login` and keep a sessionId in `state.nodeSessionId`.
- Discovery, polling, and commands use the bridge endpoints instead of direct cloud calls.

## Notes

- Sessions are in-memory; restarting the bridge clears sessions. You can re-login from the Hubitat app quickly.
- The bridge automatically refreshes access tokens using the refresh token when expired.
- The bridge uses the correct host routing: `api2.afero.net` for core and `semantics2.afero.net` for metadevice data.

