metadata {
    definition(name: 'HubSpace Thermostat', namespace: 'neerpatel/hubspace', author: 'Neer Patel') {
        capability 'Initialize'
        capability 'Refresh'
        capability 'Thermostat'
        capability 'ThermostatMode'
        capability 'ThermostatFanMode'
        capability 'TemperatureMeasurement'
        capability 'ThermostatOperatingState'
        capability 'ThermostatSetpoint'

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
}

def initialize() { }
def refresh() { parent.pollChild(device) }

def setHeatingSetpoint(temperature) {
    parent.sendHsCommand(id(), "temperature", [instance: "heating-target", value: temperature as float])
}

def setCoolingSetpoint(temperature) {
    parent.sendHsCommand(id(), "temperature", [instance: "cooling-target", value: temperature as float])
}

def setThermostatMode(mode) {
    parent.sendHsCommand(id(), "mode", [value: mode])
}

def setFanMode(fanMode) {
    parent.sendHsCommand(id(), "fan-mode", [value: fanMode])
}

def setTemperatureRange(tempLow, tempHigh) {
    parent.sendHsCommand(id(), "temperature", [instance: "auto-heating-target", value: tempLow as float])
    parent.sendHsCommand(id(), "temperature", [instance: "auto-cooling-target", value: tempHigh as float])
}

// Custom commands for safety temperatures
def setSafetyMaxTemp(temperature) {
    parent.sendHsCommand(id(), "temperature", [instance: "safety-mode-max-temp", value: temperature as float])
}

def setSafetyMinTemp(temperature) {
    parent.sendHsCommand(id(), "temperature", [instance: "safety-mode-min-temp", value: temperature as float])
}

private id() { device.deviceNetworkId - 'hubspace-' }
