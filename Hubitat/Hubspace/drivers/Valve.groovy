metadata {
  definition(name: "HubSpace Valve", namespace: "neerpatel/hubspace", author: "Neer Patel") {
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
}

def initialize() {
  log.debug "Initializing HubSpace Valve"
}

def refresh() { 
  parent.pollChild(device) 
}

def open() { 
  log.info "Opening valve ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "on"]) 
}

def close() { 
  log.info "Closing valve ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "off"]) 
}

def on() { open() }
def off() { close() }

def setDuration(duration) {
  log.info "Setting duration to ${duration} minutes for ${device.displayName}"
  parent.sendHsCommand(id(), "timer-duration", [value: duration as int])
}

def startWithDuration(duration) {
  setDuration(duration)
  open()
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
