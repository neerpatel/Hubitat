---
description: "Hubitat App - Development Guide"
applyTo: "**"
---

# Hubitat App - Development Guide

This guide provides an overview of developing Hubitat apps using Groovy, focusing on best practices, coding standards, and app structure. It is designed to help developers create efficient, maintainable, and user-friendly applications for the Hubitat platform.

## App Metadata Definition

```groovy
// Define your app
definition(
    name: "My Automation App",
    namespace: "yournamespace",
    author: "Your Name",
    description: "Creates automations based on device events",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/your/repo/main/app.groovy"
)

preferences {
    page(name: "mainPage")
}
```

## Preferences & UI Pages

```groovy
// Dynamic UI
page(name: "mainPage", title: "Configure Automation", install: true, uninstall: true) {
    section("Choose devices") {
        input "motionSensor", "capability.motionSensor", title: "Select motion sensor", multiple: false, required: true
        input "switchDevice", "capability.switch", title: "Switch to control", multiple: false, required: true
    }
    section("Options") {
        input "delay", "number", title: "Delay in seconds", defaultValue: 10
    }
}
```

## App Lifecycle Methods

```groovy
// Called when app is installed
void installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

// Called when settings are updated
void updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    unschedule()
    initialize()
}

// Common initialization logic
void initialize() {
    subscribe(motionSensor, "motion.active", motionHandler)
}

// Event handler
void motionHandler(evt) {
    log.info "Motion detected: ${evt.device}"
    runIn(delay ?: 10, turnOnSwitch)
}

void turnOnSwitch() {
    switchDevice.on()
}
```

## Logging Best Practices

```groovy
log.debug "Debugging information"
log.info "User-level info"
log.warn "Potential issues"
log.error "Error occurred"
```

## Scheduling

```groovy
// Run method in X seconds
runIn(30, someMethod)

// Cron-style schedule
schedule("0 0 * * * ?", someMethod) // Every hour
```

## OAuth & API Endpoints

```groovy
// Enable OAuth in app settings to use mappings
mappings {
    path("/devices") {
        action: [ GET: "listDevices" ]
    }
}

def listDevices() {
    return [devices: settings.switchDevice?.displayName]
}
```

## Best Practices

- Use `log.debug` for development, `log.info` for production-relevant logging.
- Minimize use of `state`; prefer settings or event-driven logic.
- Document all input fields and event handlers.

## References

- [App Lifecycle](https://docs2.hubitat.com/en/developer/app/lifecycle)
- [Dynamic Pages](https://docs2.hubitat.com/en/developer/app/preferences)
- [App Object API](https://docs2.hubitat.com/en/developer/app/app-object)
- [OAuth Integration](https://docs2.hubitat.com/en/developer/app/oauth)
