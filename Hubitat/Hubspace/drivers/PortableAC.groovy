/*
 * ====================================================================
 *  HubSpace Portable AC (Driver)
 *
 *  Capabilities: Thermostat, ThermostatMode, ThermostatFanMode, TemperatureMeasurement
 *  Purpose:
 *  - Map Hubitat AC controls to HubSpace: 'temperature' (cooling-target), 'mode',
 *    'fan-speed' (instance: ac-fan-speed), and 'sleep'.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.1.1" }

metadata {
    definition(name: 'HubSpace Portable AC', namespace: 'neerpatel/hubspace', author: 'Neer Patel', version: deviceVer()) {
        capability 'Initialize'
        capability 'Refresh'
        capability 'Thermostat'
        capability 'ThermostatMode'
        capability 'ThermostatFanMode'
        capability 'TemperatureMeasurement'
        // Custom attributes for selects
        attribute "sleepMode", "string"

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
        input name: 'devicePollSeconds', type: 'number', title: 'Device refresh interval (sec)', description: 'Override app polling for this device', required: false
    }
}
def initialize() { log.debug "Initializing HubSpace Portable AC v${deviceVer()}" }
def updated() {
    try {
        if (settings?.devicePollSeconds) {
            device.updateDataValue('devicePollSeconds', String.valueOf((settings.devicePollSeconds as int)))
        } else {
            device.removeDataValue('devicePollSeconds')
        }
    } catch (ignored) {}
}
def refresh() { parent.pollChild(device) }

def setCoolingSetpoint(temperature) {
    log.info "Set cooling setpoint ${temperature} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: 'coolingSetpoint', value: (temperature as float))
    parent.sendHsCommand(id(), "temperature", [instance: "cooling-target", value: temperature as float])
}

def setThermostatMode(mode) {
    log.info "Set thermostat mode ${mode} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: 'thermostatMode', value: mode as String)
    parent.sendHsCommand(id(), "mode", [value: mode])
}

def setFanMode(fanMode) {
    log.info "Set fan speed ${fanMode} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: 'thermostatFanMode', value: fanMode as String)
    parent.sendHsCommand(id(), "fan-speed", [instance: "ac-fan-speed", value: fanMode])
}

def setSleepMode(mode) {
    log.info "Set sleep mode ${mode} (drv v${deviceVer()}) for ${device.displayName}"
    sendEvent(name: 'sleepMode', value: mode as String)
    parent.sendHsCommand(id(), "sleep", [value: mode])
}

private id() { device.deviceNetworkId - 'hubspace-' }
