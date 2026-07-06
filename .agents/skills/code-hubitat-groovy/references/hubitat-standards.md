# Hubitat Groovy Standards

## Official References

- Developer home: https://docs2.hubitat.com/
- Developer overview: https://docs2.hubitat.com/pt-br/developer/overview
- App overview: https://docs2.hubitat.com/pt-br/developer/app/overview
- App definition: https://docs2.hubitat.com/developer/app/definition
- App code: https://docs2.hubitat.com/developer/app/code
- Installed app object: https://docs2.hubitat.com/developer/app/installedapp-object
- Driver overview: https://docs2.hubitat.com/pt-br/developer/driver/overview
- Best practices: https://docs2.hubitat.com/developer/best-practices

## Sandbox Constraints

Hubitat apps and drivers are Groovy scripts executed in the Hubitat sandbox. Avoid patterns that belong to normal JVM apps:

- No custom JARs.
- No custom threads.
- No `sleep()`.
- No `println`; use `log`.
- No unrestricted class imports.
- No assumption that arbitrary Java/Groovy APIs are available.

## App Standards

Apps should normally include:

- `definition(...)`
- `preferences { ... }`
- `installed()`
- `updated()`
- `uninstalled()` when external cleanup is required
- event handlers for `subscribe()`
- scheduled handlers for `runIn`, `schedule`, or `runEvery*`

Use this update pattern:

```groovy
def updated() {
    unsubscribe()
    unschedule()
    initialize()
}
```

## Driver Standards

Drivers should normally include:

- `metadata { definition(...) { ... } preferences { ... } }`
- required methods for declared capabilities
- `installed()`
- `updated()`
- `initialize()` when using `capability "Initialize"` or reconnecting/polling
- `parse(String desc)` for protocol/LAN/cloud responses when applicable
- `sendEvent(name: "...", value: ...)` for attribute updates

## State And Settings

- `settings` are user preferences.
- `state` is persisted app/device state saved between executions.
- `atomicState` persists immediately and should be used sparingly.
- Keep persisted values small and serializable.

## Logging

Prefer:

```groovy
if (debugLogging) log.debug "Useful debug message"
log.warn "Recoverable issue"
log.error "Failure details"
```

Avoid noisy logs inside frequent event handlers unless debug logging is enabled.
