/*
 * ====================================================================
 *  HubSpace Exhaust Fan (Driver)
 *
 *  Capabilities: Initialize, Refresh, MotionSensor, RelativeHumidityMeasurement
 *  Purpose:
 *  - Exposes controls for HubSpace exhaust fan settings:
 *    'auto-off-timer' (number), 'motion-action' (enum), 'sensitivity' (enum).
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.1.1" }

metadata {
    definition(name: "HubSpace Exhaust Fan", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer(), importUrl: "https://raw.githubusercontent.com/neerpatel/Hubitat/refs/heads/main/Hubitat/Hubspace/drivers/ExhaustFan.groovy") {
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
    preferences {
        input name: 'devicePollSeconds', type: 'number', title: 'Device refresh interval (sec)', description: 'Override app polling for this device', required: false
    }
}
def initialize() { log.debug "Initializing HubSpace Exhaust Fan v${deviceVer()}" }
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

// Commands for numbers (e.g., auto-off-timer)
def setNumber(functionClass, functionInstance, value) {
    log.info "Set ${functionClass}/${functionInstance} -> ${value} (drv v${deviceVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

// Commands for selects (e.g., motion-action, sensitivity)
def setSelect(functionClass, functionInstance, value) {
    log.info "Set ${functionClass}/${functionInstance} -> ${value} (drv v${deviceVer()}) for ${device.displayName}"
    parent.sendHsCommand(id(), functionClass, [instance: functionInstance, value: value])
}

def setAutoOffTimer(seconds) {
    setNumber('auto-off-timer', 'auto-off', seconds as int)
    sendEvent(name: 'autoOffTimer', value: (seconds as int))
}

def setMotionAction(action) {
    setSelect('motion-action', 'exhaust-fan', action)
    sendEvent(name: 'motionAction', value: action as String)
}

def setHumiditySensitivity(level) {
    setSelect('sensitivity', 'humidity-sensitivity', level)
    sendEvent(name: 'sensitivity', value: level as String)
}

private id() { device.deviceNetworkId - 'hubspace-' }
