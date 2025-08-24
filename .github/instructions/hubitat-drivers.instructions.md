---
description: "Hubitat Driver - Development Guide"
applyTo: "**"
---

# Hubitat Driver - Development Guide

This guide provides an overview of developing Hubitat drivers using Groovy, focusing on best practices, coding standards, and driver structure. It is designed to help developers create efficient, maintainable, and user-friendly drivers for the Hubitat platform.

## Driver Metadata Definition

```groovy
metadata {
    definition(name: "My Zigbee Dimmer", namespace: "yournamespace", author: "Your Name") {
        capability "Switch"
        capability "Switch Level"

        command "customCommand"
        attribute "lastUpdated", "string"

        fingerprint profileId: "0104", inClusters: "0006,0008", outClusters: "", manufacturer: "XYZ", model: "Dimmer123"
    }
}
```

## Required Lifecycle Methods

```groovy
void installed() {
    log.debug "Device installed"
    initialize()
}

void updated() {
    log.debug "Device updated"
    initialize()
}

void initialize() {
    log.debug "Initializing device"
}
```

## Parse Method for Zigbee

```groovy
// Main parser for incoming messages
def parse(String description) {
    log.debug "Parsing: ${description}"
    def event = zigbee.getEvent(description)
    if (event) {
        sendEvent(event)
    } else {
        log.warn "No matching event for: $description"
    }
}
```

## Handling Commands

```groovy
// Turn device on
void on() {
    log.info "Turning on"
    return zigbee.on()
}

// Set dimmer level
void setLevel(level, duration = null) {
    log.info "Setting level to ${level}"
    def cmds = zigbee.setLevel(level)
    return cmds
}
```

## Sending Events

```groovy
sendEvent(name: "switch", value: "on")
sendEvent(name: "level", value: 75)
```

## Hub Actions

```groovy
// Advanced communication
sendHubCommand(new hubitat.device.HubAction("raw command", hubitat.device.Protocol.LAN))
```

## Parent/Child Communication

```groovy
// In parent:
def childOn(dni) {
    def child = getChildDevice(dni)
    child?.sendEvent(name: "switch", value: "on")
}

// In child:
def parent = getParent()
parent.someParentMethod()
```

## Best Practices

- Define only supported capabilities.
- Use `parse()` for incoming Zigbee/Z-Wave messages.
- Minimize state; use attributes to store external state.
- Comment custom fingerprints and command handling.

## References

- [Driver Definition](https://docs2.hubitat.com/en/developer/driver/definition)
- [Building Zigbee Drivers](https://docs2.hubitat.com/en/developer/driver/building-a-zigbee-driver)
- [parse() Method](https://docs2.hubitat.com/en/developer/driver/parse)
- [Parent/Child Drivers](https://docs2.hubitat.com/en/developer/driver/parent-child-drivers)
