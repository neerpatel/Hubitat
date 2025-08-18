metadata {
  definition(name: "HubSpace Security System Sensor", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "ContactSensor"
    capability "MotionSensor"
    capability "Refresh"
    capability "PresenceSensor"
    capability "Battery"
    
    attribute "sensorType", "string"
    attribute "triggerMode", "enum", ["off", "home", "away", "home/away"]
    attribute "chirpMode", "enum", ["off", "on"]
    attribute "bypassMode", "enum", ["off", "on"]

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
    
    command "setTriggerMode", [[name:"mode", type:"ENUM", constraints:["off", "home", "away", "home/away"]]]
    command "setChirpMode", [[name:"mode", type:"ENUM", constraints:["off", "on"]]]
    command "setBypassMode", [[name:"mode", type:"ENUM", constraints:["off", "on"]]]
  }
}

def initialize() {
  log.debug "Initializing HubSpace Security System Sensor"
}

def refresh() { 
  parent.pollChild(device) 
}

def setTriggerMode(mode) {
  log.info "Setting trigger mode to ${mode} for ${device.displayName}"
  def triggerValue = mode == "home/away" ? 3 : (mode == "away" ? 2 : (mode == "home" ? 1 : 0))
  parent.sendHsCommand(id(), "sensor-config", [
    instance: device.getDataValue("sensorInstance") ?: "sensor-1",
    value: [
      triggerType: triggerValue,
      chirpMode: device.currentValue("chirpMode") == "on" ? 1 : 0,
      bypassType: device.currentValue("bypassMode") == "on" ? 1 : 0
    ]
  ])
}

def setChirpMode(mode) {
  log.info "Setting chirp mode to ${mode} for ${device.displayName}"
  def chirpValue = mode == "on" ? 1 : 0
  parent.sendHsCommand(id(), "sensor-config", [
    instance: device.getDataValue("sensorInstance") ?: "sensor-1",
    value: [
      triggerType: getTriggerValue(device.currentValue("triggerMode")),
      chirpMode: chirpValue,
      bypassType: device.currentValue("bypassMode") == "on" ? 1 : 0
    ]
  ])
}

def setBypassMode(mode) {
  log.info "Setting bypass mode to ${mode} for ${device.displayName}"
  def bypassValue = mode == "on" ? 1 : 0
  parent.sendHsCommand(id(), "sensor-config", [
    instance: device.getDataValue("sensorInstance") ?: "sensor-1",
    value: [
      triggerType: getTriggerValue(device.currentValue("triggerMode")),
      chirpMode: device.currentValue("chirpMode") == "on" ? 1 : 0,
      bypassType: bypassValue
    ]
  ])
}

private getTriggerValue(mode) {
  switch(mode) {
    case "home/away": return 3
    case "away": return 2
    case "home": return 1
    default: return 0
  }
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
