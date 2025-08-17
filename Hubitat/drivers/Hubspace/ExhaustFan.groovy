metadata {
    definition(name: 'HubSpace Exhaust Fan', namespace: 'neerpatel/hubspace', author: 'Neer Patel') {
        capability 'Initialize'
        capability 'Refresh'
        capability 'MotionSensor' // for motion-detection
        capability 'RelativeHumidityMeasurement' // for humidity-threshold-met
        // Add other capabilities as needed for numbers and selects
    }
}

def initialize() {
    log.debug "Initializing HubSpace Exhaust Fan"
    refresh()
}
def refresh() { parent.pollChild(device) }

// Commands for numbers (e.g., auto-off-timer)
def setNumber(functionClass, functionInstance, value) {
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

// Commands for selects (e.g., motion-action, sensitivity)
def setSelect(functionClass, functionInstance, value) {
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

private id() {
  device.deviceNetworkId - "hubspace-"
}
