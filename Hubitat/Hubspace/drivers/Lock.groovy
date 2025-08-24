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

String deviceVer() { return "0.1.1" }

metadata {
  definition(name: "HubSpace Lock", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer()) {
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
  preferences {
    input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
  }
}
def initialize() { log.debug "Initializing HubSpace Lock v${deviceVer()}" }
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

def lock() { 
  log.info "Locking ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'lock', value: 'locked')
  parent.sendHsCommand(id(), "lock", [value: "locked"]) 
}

def unlock() { 
  log.info "Unlocking ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'lock', value: 'unlocked')
  parent.sendHsCommand(id(), "lock", [value: "unlocked"]) 
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
