# HubSpace Integration for Hubitat

This integration now runs directly inside the Hubitat app. The Hubitat app handles HubSpace login, token refresh, device discovery, polling, and commands without requiring a separately hosted proxy.

`bridge-node/` is still present in the repo as a legacy fallback/reference implementation, but the primary install path is the Groovy app in [`Hubitat/Hubspace/app/HubspaceDeviceManager.groovy`](/Users/neerpatel/workspace/repos/Hubitat/Hubitat/Hubspace/app/HubspaceDeviceManager.groovy).

## Architecture

```
Hubitat Hub <-> HubSpace Device Manager app <-> HubSpace Cloud
```

The app reproduces the same private HubSpace mobile-client auth flow the old bridge used:

1. GET the HubSpace login page and collect the login form and cookies.
2. POST HubSpace credentials to the login form action.
3. Extract the auth code from the mobile redirect URL.
4. Exchange the code for tokens with PKCE.
5. Refresh tokens inside the app before they expire.

## Setup

1. Import the app from `Hubitat/Hubspace/app/HubspaceDeviceManager.groovy`.
2. Import the drivers in `Hubitat/Hubspace/drivers/`.
3. Install the app in Hubitat.
4. Enter your HubSpace username and password.
5. Click `Connect to HubSpace`.
6. Run `Discover Devices Now`, then add the devices you want.

## App Settings

| Setting | Description |
|---|---|
| `username` | HubSpace account email |
| `password` | HubSpace account password |
| `pollSeconds` | Default poll interval for child devices |

Some drivers expose an optional per-device poll override through `devicePollSeconds`.

## Supported Behavior

- Direct cloud authentication from the app
- Automatic token refresh
- Device discovery from HubSpace metadevices
- Child-device creation for supported HubSpace device classes
- Poll-based state updates
- Command routing through `parent.sendHsCommand(...)`

## Current Limits

- Verification-code / OTP login is not supported.
- This integration still depends on HubSpace’s private mobile-client auth behavior. If HubSpace changes that flow, the Groovy app will need to be updated.
- `bridge-node/` has not been deleted yet because it remains useful as a fallback/reference path while validating the Groovy flow on real hubs.

## Manual Validation

In Hubitat logs, validate at least:

1. `Connect to HubSpace` completes and logs an account ID.
2. `Discover Devices Now` returns the expected devices.
3. A light can `on`, `off`, and `setLevel`.
4. A fan or thermostat can run one non-switch command.
5. A manual `refresh()` on a child device updates state.
6. The app still removes selected devices and uninstall removes all child devices.

## Troubleshooting

### Login fails immediately

- Re-enter HubSpace credentials.
- Check logs for `HubSpace login failed`.
- If the account now requires a verification code, this app will not complete login.

### Discovery returns no devices

- Confirm the same HubSpace account still has devices in the mobile app.
- Reconnect from the app page, then run discovery again.

### Commands stop working after some time

- Check logs for token refresh or unauthorized errors.
- Reconnect from the app page to force a fresh login.
