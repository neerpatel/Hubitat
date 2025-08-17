metadata {
    definition(name: 'HubSpace Portable AC', namespace: 'neerpatel/hubspace', author: 'Neer Patel') {
        capability 'Initialize'
        capability 'Refresh'
        capability 'Thermostat'
        capability 'ThermostatMode'
        capability 'ThermostatFanMode'
        capability 'TemperatureMeasurement'
        // Custom attributes for selects
        attribute "sleepMode", "string"
    }
}

def initialize() {
    log.debug "Initializing HubSpace Portable AC"
    refresh()
}
def refresh() { parent.pollChild(device) }

def setCoolingSetpoint(temperature) {
    parent.sendHsCommand(id(), "temperature", [instance: "cooling-target", value: temperature as float])
}

def setThermostatMode(mode) {
    parent.sendHsCommand(id(), "mode", [value: mode])
}

def setFanMode(fanMode) {
    parent.sendHsCommand(id(), "fan-speed", [instance: "ac-fan-speed", value: fanMode])
}

def setSleepMode(mode) {
    parent.sendHsCommand(id(), "sleep", [value: mode])
}

private id() {
  device.deviceNetworkId - "hubspace-"
}
