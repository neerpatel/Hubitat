metadata {
    definition(name: 'HubSpace Exhaust Fan', namespace: 'neerpatel/hubspace', author: 'Neer Patel') {
        capability 'Initialize'
        capability 'Refresh'
        capability 'MotionSensor' // for motion-detection
        capability 'RelativeHumidityMeasurement' // for humidity-threshold-met
        // Add other capabilities as needed for numbers and selects

        attribute 'autoOffTimer', 'number'
        attribute 'motionAction', 'string'
        attribute 'sensitivity', 'string'

        // Network/health telemetry surfaced by the app
        attribute 'ssid', 'string'
        attribute 'rssi', 'number'
        attribute 'wifiState', 'string'
        attribute 'wifiSetupState', 'string'
        attribute 'wifiMac', 'string'
        attribute 'visible', 'string'
        attribute 'direct', 'string'
        attribute 'healthStatus', 'string'
        attribute 'latitude', 'number'
        attribute 'longitude', 'number'
        attribute 'location', 'string'
        attribute 'schedulerFlags', 'string'

        command 'setAutoOffTimer', [[name: 'seconds', type: 'NUMBER']]
        command 'setMotionAction', [[name: 'action', type: 'ENUM', constraints: ['off','on','timer']]]
        command 'setHumiditySensitivity', [[name: 'level', type: 'ENUM', constraints: ['low','medium','high']]]
    }
}

def initialize() { }
def refresh() { parent.pollChild(device) }

// Commands for numbers (e.g., auto-off-timer)
def setNumber(functionClass, functionInstance, value) {
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

// Commands for selects (e.g., motion-action, sensitivity)
def setSelect(functionClass, functionInstance, value) {
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

def setAutoOffTimer(seconds) {
    setNumber('auto-off-timer', 'auto-off', seconds as int)
}

def setMotionAction(action) {
    setSelect('motion-action', 'exhaust-fan', action)
}

def setHumiditySensitivity(level) {
    setSelect('sensitivity', 'humidity-sensitivity', level)
}

private id() { device.deviceNetworkId - 'hubspace-' }
