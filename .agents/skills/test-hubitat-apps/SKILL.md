---
name: test-hubitat-apps
description: Add automated tests and release checks for Hubitat Elevation Groovy apps and drivers. Use when Codex is asked to test Hubitat apps, drivers, migrated Home Assistant logic, event handlers, preferences, schedules, commands, state transitions, metadata validation, virtual-device smoke tests, CI setup, or pre-publishing quality gates for Hubitat code.
---

# Test Hubitat Apps

## Strategy

Use a layered test approach:

1. **Pure logic tests** for decisions that do not need Hubitat APIs.
2. **Hubitat script tests** for lifecycle methods, metadata, preferences, settings, state, subscriptions, schedules, and mocked device commands.
3. **Scenario/contract tests** for behavior parity, especially during Home Assistant migration.
4. **Dev-hub smoke tests** with virtual devices.
5. **Release checks** before publishing or packaging.

Read `references/test-patterns.md` before writing substantial tests or CI.

## Local Test Guidance

- Prefer Spock for Groovy tests when the repo already uses Gradle/Groovy.
- Prefer `hubitat_ci` when tests need to load a Hubitat `.groovy` app or driver script and mock Hubitat APIs.
- Keep business logic in functions that accept primitive values, maps, or simple collections.
- Test event handlers separately from the decision logic they call.
- Mock Hubitat-provided methods and collaborators rather than trying to run a full hub locally.
- Validate metadata, preferences, command names, capability declarations, and settings assumptions.

## Scenario Tests

For migrated behavior, capture scenarios in plain YAML/JSON or table-style test data:

```yaml
name: turns light on when motion active and lux is low
given:
  mode: Home
  motion: active
  lux: 25
  threshold: 50
expect:
  command:
    device: kitchen_light
    action: "on"
```

Use these scenarios to drive either pure logic tests or Hubitat script tests.

## Dev-Hub Smoke Checks

After local tests pass:

- Install code on a non-production Hubitat hub.
- Use virtual switches, dimmers, motion sensors, contact sensors, buttons, and presence devices where possible.
- Exercise `installed()`, `updated()`, subscriptions, scheduled jobs, and uninstall cleanup.
- Check logs for errors, noisy debug output, missed events, and unexpected duplicate commands.
- Verify real devices or external APIs only after virtual-device tests pass.

## Release Gate

Before publishing:

- All local tests pass.
- The app/driver saves successfully in Hubitat.
- Lifecycle callbacks succeed: `installed()`, `updated()`, and `uninstalled()` where applicable.
- Commands and attributes match declared capabilities.
- Preferences have safe defaults and validation.
- Scheduling and subscriptions are cleaned up during updates/uninstall.
- Logs are useful and can be reduced or disabled for normal use.
- Known migration gaps are documented.

## References

- `references/test-patterns.md`: load for concrete test patterns and example snippets.
- `references/release-checklist.md`: load before publishing or packaging.
