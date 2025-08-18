metadata {
  definition(name: "HubSpace Lock", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Lock"
    capability "Refresh"
    capability "Battery"
    capability "PresenceSensor"
    
    command "unlock"
    command "lock"

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

def initialize() {
  log.debug "Initializing HubSpace Lock"
}

def refresh() { 
  parent.pollChild(device) 
}

def lock() { 
  log.info "Locking ${device.displayName}"
  parent.sendHsCommand(id(), "lock", [value: "locked"]) 
}

def unlock() { 
  log.info "Unlocking ${device.displayName}"
  parent.sendHsCommand(id(), "lock", [value: "unlocked"]) 
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
