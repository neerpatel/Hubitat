/*
 * ====================================================================
 *  HubSpace Lock (Driver)
 *
 *  Capabilities: Lock, Battery
 *  Purpose:
 *  - Map Hubitat lock/unlock to HubSpace 'lock' function with values 'locked'/'unlocked'.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String driverVer() { return "0.1.0" }

metadata {
  definition(name: "HubSpace Lock", namespace: "neerpatel/hubspace", author: "Neer Patel", version: "0.1.0") {
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
def initialize() { log.debug "Initializing HubSpace Lock v${driverVer()}" }

def refresh() { parent.pollChild(device) }

def lock() { 
  log.info "Locking ${device.displayName} (drv v${driverVer()})"
  parent.sendHsCommand(id(), "lock", [value: "locked"]) 
}

def unlock() { 
  log.info "Unlocking ${device.displayName} (drv v${driverVer()})"
  parent.sendHsCommand(id(), "lock", [value: "unlocked"]) 
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
