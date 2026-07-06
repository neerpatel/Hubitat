# Porting Checklist

Use before finalizing a Home Assistant to Hubitat migration.

## Behavior Inventory

- Source triggers are listed.
- Source conditions are listed.
- Source actions/service calls are listed.
- Source state and persistence are listed.
- User-configurable options are listed.
- External API or LAN dependencies are listed.
- Startup, reload, unavailable, and error behavior are understood.

## Hubitat Design

- App vs driver choice is explicit.
- Capabilities and attributes are standard where possible.
- Preferences cover user configuration.
- Device selection inputs use appropriate capabilities.
- Child devices are used only when they add real value.
- Scheduling and subscriptions are initialized in `installed()`/`updated()`.
- `unschedule()` and unsubscribe cleanup are used when settings change or app uninstalls.

## Test Parity

- At least one happy path scenario exists.
- At least one negative/no-op scenario exists.
- Important edge cases are covered.
- Tests prove command calls, state changes, or emitted events.
- Dev-hub virtual-device smoke steps are listed.

## Release Readiness

- Code saves in Hubitat without syntax errors.
- Normal logs are not too noisy.
- Debug logging can be disabled or auto-disabled.
- Known gaps from HA behavior are documented.
- Version/import URL metadata is updated if publishing.
