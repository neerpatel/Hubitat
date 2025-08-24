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

String driverVer() { return "0.1.0" }

metadata {
    definition(name: 'HubSpace Portable AC', namespace: 'neerpatel/hubspace', author: 'Neer Patel', version: '0.1.0') {
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
def initialize() { log.debug "Initializing HubSpace Portable AC v${driverVer()}" }
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
    log.info "Set cooling setpoint ${temperature} (drv v${driverVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), "temperature", [instance: "cooling-target", value: temperature as float])
}

def setThermostatMode(mode) {
    log.info "Set thermostat mode ${mode} (drv v${driverVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), "mode", [value: mode])
}

def setFanMode(fanMode) {
    log.info "Set fan speed ${fanMode} (drv v${driverVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), "fan-speed", [instance: "ac-fan-speed", value: fanMode])
}

def setSleepMode(mode) {
    log.info "Set sleep mode ${mode} (drv v${driverVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), "sleep", [value: mode])
}

private id() { device.deviceNetworkId - 'hubspace-' }
