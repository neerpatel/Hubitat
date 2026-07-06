# Repository Guidelines

## Project Structure & Architecture

This repo centers on the HubSpace integration for Hubitat. The Hubitat app lives in `Hubitat/Hubspace/app/HubspaceDeviceManager.groovy` and owns OAuth, discovery, polling, and command routing. Device drivers live in `Hubitat/Hubspace/drivers/` with one Groovy file per device type, for example `Light.groovy`, `Fan.groovy`, and `Thermostat.groovy`. The optional bridge server lives in `bridge-node/` and exposes the cloud proxy used by the Hubitat app. Long-form setup and protocol notes belong in `docs/`, especially `docs/Hubspace.md`.

## Build, Test, and Development Commands

- `cd bridge-node && npm install`: install bridge dependencies.
- `cd bridge-node && npm start`: run the bridge with Node.
- `cd bridge-node && npm run dev`: run the bridge with `nodemon`.
- `curl http://localhost:3000/health`: quick bridge smoke test.
- `bash setup.sh`: provision the deployment host and PM2 layout.
- `bash post-deploy.sh`: refresh bridge dependencies after deploy.

Hubitat Groovy files are loaded through the Hubitat admin UI, not a local build tool. Import the app/driver code, run discovery, then verify device events in hub logs.

## Coding Style & Naming Conventions

Use 4 spaces in both Groovy and JavaScript. Keep Groovy script-style and Hubitat-native: `UpperCamelCase.groovy` filenames, Hubitat capability names, and helper methods before lifecycle handlers when practical. Use `camelCase` for methods and variables. In the bridge, follow the existing CommonJS and minimal Express style; avoid adding abstraction unless a second caller needs it.

## Testing & Validation

There is no committed automated test suite yet, so keep validation targeted and reproducible. For Hubitat changes, include manual steps such as `on`, `setLevel`, `lock`, or `setThermostatMode`, plus the observed log output. For bridge changes, verify `/health`, `/devices`, `/state/:id`, and `/command/:id` with `curl`.

## Commits, PRs, and Releases

Follow the repo's existing Conventional Commit pattern: `feat(scope): ...`, `fix(scope): ...`, or `chore: ...`. PRs should explain what changed, why, affected device types, and how you validated it. If behavior changes, bump versions in the touched Groovy files (`appVersion()` or `deviceVer()`), update `Hubitat/Hubspace/packageManifest.json` when needed, and add a new top entry to the matching `release-notes.md`. Never commit credentials or log raw account secrets.
