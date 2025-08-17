# Repository Guidelines

## Architecture Overview
- Hubitat app: `Hubitat/app/Hubspace/HubspaceDeviceManager.groovy` handles HubSpace OAuth/PKCE, discovery, child creation, polling, and command routing via `sendHsCommand`.
- Device drivers: `Hubitat/drivers/Hubspace/` implement Hubitat capabilities and map attributes/commands to HubSpace function classes (e.g., `power`, `brightness`).
- Optional bridge: `bridge-app/Hubspace/bridge.py` (FastAPI + `aioafero`) for local REST control and testing.

## Project Structure
- `Hubitat/app/Hubspace/`: App code and lifecycle (install, update, discovery).
- `Hubitat/drivers/Hubspace/`: Drivers like `Light.groovy`, `Switch.groovy`, `Fan.groovy`, `Lock.groovy`, `Thermostat.groovy`, `Valve.groovy`, `SecuritySystem*.groovy`.
- `bridge-app/Hubspace/bridge.py`: Login, `/devices`, `/state/{id}`, `/command/{id}`.
- `docs/`: HubSpace/Afero notes and sequence diagrams.

## Build, Test, and Run
- Hubitat: Import `.groovy` in Hubitat admin (Apps/Drivers Code). Configure credentials in the app, click “Discover Devices”, verify logs.
- Python bridge: `python3 -m venv .venv && source .venv/bin/activate && pip install fastapi uvicorn aioafero && uvicorn bridge-app.Hubspace.bridge:app --reload`
- Quick checks: `curl -X POST localhost:8000/login -H 'Content-Type: application/json' -d '{"username":"u","password":"p"}'` then `curl localhost:8000/devices`.

## Coding Style & Naming
- Python: PEP 8, 4 spaces, `snake_case`; async endpoints; keep modules under `bridge-app/Hubspace/`.
- Groovy: 4 spaces; class/file names `UpperCamelCase`; use Hubitat capabilities and command names consistently.

## Testing Guidelines
- Hubitat: Validate driver events in logs; exercise capabilities (e.g., `on`, `setLevel`, `lock`); include reproduction steps/screens.
- Python: Add targeted tests under `bridge-app/tests/` when extending; provide curl samples in PRs.

## Commit & PR Guidelines
- Commits: Imperative subject, concise body; reference issues (e.g., `#123`).
- PRs: What/why, linked issues, test evidence (logs/curl), and any config notes; keep scope tight.

## Agent-Specific Instructions
- Role: Act as an expert Python & Groovy developer to build the Hubitat app and device drivers for HubSpace devices.
- When adding a new device type:
  - Create `Hubitat/drivers/Hubspace/<Device>.groovy` with correct `capability`/`command` set. Map commands to HubSpace via `parent.sendHsCommand(device.deviceNetworkId - 'hubspace-', 'power', [value: 'on'])`, `brightness`/`color-temperature`, etc.
  - Update `driverForType(...)` in the app to return the new driver name.
  - Expand `processStateValue(...)` for any new function classes to emit Hubitat events.
- When extending the bridge, add endpoints or controller calls in `bridge.py` and keep responses minimal (`id`, `type`, `name`, `states`).
- Never log or commit credentials; use app preferences or `/login` only.
