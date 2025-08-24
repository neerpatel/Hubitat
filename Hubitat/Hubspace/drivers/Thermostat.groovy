/*
 * ====================================================================
 *  HubSpace Thermostat (Driver)
 *
 *  Capabilities: Thermostat, ThermostatMode, ThermostatFanMode, TemperatureMeasurement,
 *                ThermostatOperatingState, ThermostatSetpoint
 *  Purpose:
 *  - Map Hubitat thermostat controls to HubSpace function classes:
 *    "temperature" (instances: heating-target, cooling-target, auto-*)
 *    "mode", "fan-mode".
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.1.1" }

metadata {
    definition(name: "HubSpace Thermostat", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer(), importUrl: "https://raw.githubusercontent.com/neerpatel/Hubitat/refs/heads/main/Hubitat/Hubspace/drivers/Thermostat.groovy") {
        capability "Initialize"
        capability "Refresh"
        capability "Thermostat"
        capability "ThermostatMode"
        capability "ThermostatFanMode"
        capability "TemperatureMeasurement"
        capability "ThermostatOperatingState"
        capability "ThermostatSetpoint"

        // Custom attributes for safety temps
        attribute "safetyMaxTemp", "number"
        attribute "safetyMinTemp", "number"

        // Network/health telemetry surfaced by the app
        attribute "ssid", "string"
        attribute "rssi", "number"
        attribute "wifiState", "string"
        attribute "wifiSetupState", "string"
        attribute "wifiMac", "string"
        attribute "visible", "string"
        attribute "direct", "string"
        attribute "healthStatus", "string"
        attribute "latitude", "number"
        attribute "longitude", "number"
        attribute "location", "string"
        attribute "schedulerFlags", "string"
    }
    preferences {
        input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
    }
}
def initialize() { log.debug "Initializing HubSpace Thermostat v${deviceVer()}" }
def updated() {
    try {
        if (settings?.devicePollSeconds) {
            device.updateDataValue("devicePollSeconds", String.valueOf((settings.devicePollSeconds as int)))
        } else {
            device.removeDataValue("devicePollSeconds")
        }
    } catch (ignored) {}
}
def refresh() { parent.pollChild(device) }

def setHeatingSetpoint(temperature) {
    log.info "Set heating setpoint ${temperature} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "heatingSetpoint", value: (temperature as float))
    parent.sendHsCommand(id(), "temperature", [instance: "heating-target", value: temperature as float])
}

def setCoolingSetpoint(temperature) {
    log.info "Set cooling setpoint ${temperature} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "coolingSetpoint", value: (temperature as float))
    parent.sendHsCommand(id(), "temperature", [instance: "cooling-target", value: temperature as float])
}

def setThermostatMode(mode) {
    log.info "Set thermostat mode ${mode} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "thermostatMode", value: mode as String)
    parent.sendHsCommand(id(), "mode", [value: mode])
}

def setFanMode(fanMode) {
    log.info "Set fan mode ${fanMode} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "thermostatFanMode", value: fanMode as String)
    parent.sendHsCommand(id(), "fan-mode", [value: fanMode])
}

def setTemperatureRange(tempLow, tempHigh) {
    log.info "Set temp range ${tempLow}-${tempHigh} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "heatingSetpoint", value: (tempLow as float))
    sendEvent(name: "coolingSetpoint", value: (tempHigh as float))
    parent.sendHsCommand(id(), "temperature", [instance: "auto-heating-target", value: tempLow as float])
    parent.sendHsCommand(id(), "temperature", [instance: "auto-cooling-target", value: tempHigh as float])
}

// Custom commands for safety temperatures
def setSafetyMaxTemp(temperature) {
    log.info "Set safety max temp ${temperature} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "safetyMaxTemp", value: (temperature as float))
    parent.sendHsCommand(id(), "temperature", [instance: "safety-mode-max-temp", value: temperature as float])
}

def setSafetyMinTemp(temperature) {
    log.info "Set safety min temp ${temperature} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: "safetyMinTemp", value: (temperature as float))
    parent.sendHsCommand(id(), "temperature", [instance: "safety-mode-min-temp", value: temperature as float])
}

private id() { device.deviceNetworkId - "hubspace-" }
