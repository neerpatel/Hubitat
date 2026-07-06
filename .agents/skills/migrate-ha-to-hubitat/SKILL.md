---
name: migrate-ha-to-hubitat
description: Port Home Assistant custom integrations, automations, scripts, or YAML/Python smart-home logic to Hubitat Elevation Groovy apps and drivers. Use when Codex is asked to migrate, convert, rewrite, or reimplement Home Assistant behavior for Hubitat, especially when preserving behavior with automated tests, virtual devices, app preferences, event subscriptions, schedules, device commands, or Hubitat Package Manager readiness.
---

# Migrate Home Assistant To Hubitat

## Workflow

1. Inventory the Home Assistant behavior before writing Hubitat code.
   - Identify triggers, conditions, actions, state, services, entities, timers, helpers, config options, and external API calls.
   - Convert implicit HA behavior into explicit scenarios.
   - Capture edge cases such as startup, unavailable entities, duplicate events, time windows, mode changes, and retries.

2. Classify the target Hubitat artifact.
   - Use an **app** for automations, orchestration, subscriptions, schedules, child device creation, and user-configured behavior.
   - Use a **driver** for a device abstraction, cloud/LAN/Zigbee/Z-Wave/Matter protocol translation, attributes, commands, and parse methods.
   - Use a **parent app or parent driver plus child devices** when HA creates multiple entities for one integration.

3. Translate concepts using `references/concept-map.md`.
   - Read this reference whenever the source includes HA entities, services, events, config entries, platforms, or YAML automation syntax.

4. Preserve behavior with tests before or during the port.
   - Write scenario files or test cases from the HA behavior.
   - Use the `test-hubitat-apps` skill for Hubitat test automation, local script tests, and release checks.

5. Keep Hubitat runtime code thin.
   - Put decisions in small, deterministic methods that accept plain values.
   - Keep `installed()`, `updated()`, event handlers, and scheduled handlers focused on reading settings/state, calling decision logic, and issuing commands.
   - Avoid large untestable methods that mix preferences, event handling, device commands, and calculations.

6. Verify on a dev hub before publishing.
   - Prefer virtual devices for first smoke tests.
   - Test one real device or live API path only after local tests pass.
   - Document any behavior that cannot be mapped exactly from Home Assistant to Hubitat.

## Porting Rules

- Prefer Hubitat capabilities and attributes over custom commands when a standard capability fits.
- Treat `state` as persisted app/device state; keep it small and JSON-serializable.
- Use `atomicState` only when immediate persistence semantics matter.
- Use preferences for user-configurable settings that were HA config flow options, YAML fields, input helpers, or constants.
- Use `subscribe()` for entity/event style triggers.
- Use `runIn`, `runEvery*`, `schedule`, and `unschedule` for time-based HA behavior.
- Use `sendEvent()` in drivers to update attributes.
- Use device command calls such as `switches.on()` or `device.setLevel(50)` in apps.
- Use Hubitat logging methods instead of `println`.
- Avoid unsupported Hubitat sandbox patterns: custom JARs, arbitrary classes, threads, `sleep()`, and broad Java/Groovy APIs not allowed by Hubitat.

## Expected Output

When asked to migrate an app or automation, produce:

- A short behavior inventory.
- A Hubitat app/driver design choice with rationale.
- The migrated Groovy code or targeted patch.
- Test scenarios or test files that prove parity for important behavior.
- Manual dev-hub smoke test steps.
- Known gaps where Hubitat and Home Assistant differ.

## References

- `references/concept-map.md`: load when translating Home Assistant constructs to Hubitat constructs.
- `references/porting-checklist.md`: load before finalizing a migration or preparing code for release.
