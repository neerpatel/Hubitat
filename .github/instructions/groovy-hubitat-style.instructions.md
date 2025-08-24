---
description: "Groovy & Hubitat Coding Style Guide"
applyTo: "*.groovy"
---

# Groovy & Hubitat Coding Style Guide

This guide provides a comprehensive overview of coding standards and best practices for developing Hubitat apps and drivers in Groovy. It is designed to help maintain consistency, readability, and performance across the codebase.

## Hubitat Groovy Context

- **Groovy version:** 2.4.x (no newer features like `var`, traits, or lambdas).
- **Sandboxed:** No threads, no file/network I/O.
- **No class definitions:** All code is script-level.

## Code Structure Tips

```groovy
// Define helper methods at top
void logInfo(msg) {
    log.info msg
}

// Lifecycle methods below
void installed() {
    logInfo "Installed"
    initialize()
}

void updated() {
    logInfo "Updated"
    unschedule()
    initialize()
}

void initialize() {
    // Setup event subscriptions
}
```

## Idiomatic Groovy

```groovy
// Use closures
settings.each { key, value ->
    log.debug "$key = $value"
}

// Safe navigation operator
if (device?.currentValue("switch") == "on") {
    // do something
}

// Elvis operator
def delay = settings.delay ?: 10
```

## Logging Convention

```groovy
log.trace "Verbose trace"
log.debug "Debug info"
log.info  "Useful info for user"
log.warn  "Warnings"
log.error "Critical errors"
```

## Data Storage: `state` vs `settings`

| Purpose    | `state`                | `settings`                  |
| ---------- | ---------------------- | --------------------------- |
| Mutable?   | Yes                    | No (user-defined)           |
| Use case   | Track app/driver state | Store user preferences      |
| Size limit | Yes (storage quota)    | Yes (stored on config save) |

```groovy
state.lastOn = now()
log.debug "Last turned on: ${state.lastOn}"
```

## Common Patterns

```groovy
// Schedule a handler every 5 minutes
schedule("0 0/5 * * * ?", refresh)

// Send event to device
sendEvent(name: "switch", value: "off")

// Compare device value
if (device.currentValue("temperature") > 75) {
    // turn on AC
}
```

## File Naming

- Apps: `MyApp.groovy`
- Drivers: `MyDeviceType.groovy`
- Avoid spaces or non-ASCII characters.

## VSCode Integration Notes

- Store this in `.github/copilot/groovy-hubitat-style.md`
- Copilot will use this for context when completing Hubitat-specific Groovy.

## References

- [Hubitat Development Overview](https://docs2.hubitat.com/en/developer/overview)
- [Best Practices](https://docs2.hubitat.com/en/developer/best-practices)
- [Groovy Language Docs](http://groovy-lang.org/syntax.html)
