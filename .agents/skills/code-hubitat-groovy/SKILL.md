---
name: code-hubitat-groovy
description: Write, edit, review, or refactor Hubitat Elevation Groovy apps and drivers using Hubitat sandbox conventions, lifecycle callbacks, metadata, preferences, logging, state, scheduling, subscriptions, and app/driver structure. Use when Codex is asked to create Hubitat Groovy code, fix Hubitat app or driver bugs, review Hubitat code quality, apply Hubitat developer standards, or adapt generic Groovy/SmartThings/Home Assistant logic to Hubitat-compatible Groovy.
---

# Code Hubitat Groovy

## Start Here

1. Identify whether the code is an **app**, **driver**, or shared logic intended to be pasted into one.
2. Read `references/hubitat-standards.md` before writing or reviewing non-trivial Hubitat code.
3. Use `model-hubitat-devices` when designing capabilities, attributes, commands, child devices, or driver metadata.
4. Use `test-hubitat-apps` when adding or updating tests.
5. Use `package-hubitat-code` when preparing code for release, import URLs, or Hubitat Package Manager.

## Authoring Rules

- Write Hubitat-compatible Groovy scripts, not generic JVM apps.
- Keep lifecycle callbacks thin: `installed()`, `updated()`, `uninstalled()`, `initialize()`, `parse(String desc)`.
- Centralize setup in an `initialize()` method when useful, and call it from `installed()` and `updated()`.
- Clean up schedules and subscriptions before recreating them on updates.
- Use `state` for persisted app/device state; keep values simple and serializable.
- Prefer explicit helper methods for business logic so tests can call them directly.
- Use `log.debug`, `log.info`, `log.warn`, and `log.error`; do not use `println`.
- Avoid `sleep()`, custom threads, custom JARs, broad Java APIs, and custom classes unless Hubitat explicitly supports the pattern.
- Prefer defensive guards for missing settings, null devices, unavailable values, and duplicate events.
- Use `singleThreaded: true` when concurrent handler execution could corrupt state or issue duplicate commands.

## Code Shape

For apps:

```groovy
definition(
    name: "Example App",
    namespace: "example",
    author: "Example",
    description: "Example Hubitat app",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleThreaded: true
)

preferences {
    page(name: "mainPage", title: "Example App", install: true, uninstall: true) {
        section("Devices") {
            input "switches", "capability.switch", title: "Switches", multiple: true, required: true
        }
    }
}

def installed() { initialize() }

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    subscribe(switches, "switch", switchHandler)
}
```

For drivers, put `definition` and `preferences` inside `metadata`, implement required capability commands, and use `sendEvent()` for attributes.

## Review Checklist

- App/driver type is correct for the behavior.
- Metadata includes required fields and empty unused icon fields where expected.
- Preferences have sensible defaults and required flags.
- Lifecycle callbacks do not duplicate subscriptions or schedules.
- Capability commands and attributes are implemented consistently.
- State keys are named and used consistently.
- Logs are useful, not noisy.
- Failure paths do not throw avoidable null exceptions.

## References

- `references/hubitat-standards.md`: official docs links, sandbox constraints, app/driver lifecycle, and common code conventions.
