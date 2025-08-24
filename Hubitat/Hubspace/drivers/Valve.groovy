/*
 * ====================================================================
 *  HubSpace Valve / Water Timer (Driver)
 *
 *  Capabilities: Valve, Switch, Battery, PresenceSensor
 *  Purpose:
 *  - Map open/close to HubSpace 'power' on/off.
 *  - Support timer-duration for watering time.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.1.1" }

metadata {
  definition(name: "HubSpace Valve", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer()) {
    capability "Initialize"
    capability "Valve"
    capability "Switch"
    capability "Refresh"
    capability "PresenceSensor"
    capability "Battery"
    
    attribute "duration", "number"
    attribute "remainingTime", "number"

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
    
    command "setDuration", [[name:"duration", type:"NUMBER", description:"Timer duration in minutes"]]
    command "startWithDuration", [[name:"duration", type:"NUMBER", description:"Start watering with duration in minutes"]]
  }
  preferences {
    input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
  }
}
def initialize() { log.debug "Initializing HubSpace Valve v${deviceVer()}" }
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

def open() { 
  log.info "Opening valve ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'valve', value: 'open')
  sendEvent(name: 'switch', value: 'on')
  parent.sendHsCommand(id(), "power", [value: "on"]) 
}

def close() { 
  log.info "Closing valve ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'valve', value: 'closed')
  sendEvent(name: 'switch', value: 'off')
  parent.sendHsCommand(id(), "power", [value: "off"]) 
}

def on() { open() }
def off() { close() }

def setDuration(duration) {
  log.info "Setting duration to ${duration} minutes for ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'duration', value: (duration as int))
  parent.sendHsCommand(id(), "timer-duration", [value: duration as int])
}

def startWithDuration(duration) {
  setDuration(duration)
  open()
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
