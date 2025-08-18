metadata {
  definition(name: "HubSpace Fan", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "FanControl"
    capability "Actuator"
    capability "Refresh"
    capability "PresenceSensor"
    
    command "setDirection", ["string"] // forward, reverse

    attribute "direction", "string"

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
  log.debug "Initializing HubSpace Fan"
}

def refresh() { 
  parent.pollChild(device) 
}

def on() { 
  log.info "Turning on ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "on"]) 
}

def off() { 
  log.info "Turning off ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "off"]) 
}

def setSpeed(speed) {
  log.info "Setting fan speed to ${speed} for ${device.displayName}"
  
  // Convert Hubitat speed names to numeric values for HubSpace API
  def speedValue
  switch (speed?.toLowerCase()) {
    case "off":
      speedValue = 0
      break
    case "low":
      speedValue = 1
      break
    case "medium-low":
      speedValue = 2
      break
    case "medium":
      speedValue = 3
      break
    case "medium-high":
      speedValue = 4
      break
    case "high":
      speedValue = 5
      break
    case "auto":
      speedValue = "auto"
      break
    default:
      // If it's a number, use it directly
      if (speed?.isNumber()) {
        speedValue = speed as int
      } else {
        log.warn "Unknown fan speed: ${speed}, defaulting to medium"
        speedValue = 3
      }
  }
  
  if (speedValue == 0) {
    off()
  } else {
    parent.sendHsCommand(id(), "fan-speed", [value: speedValue])
    if (device.currentValue("switch") != "on") {
      on()
    }
  }
}

def cycleSpeed() {
  def currentSpeed = device.currentValue("speed") ?: "off"
  def speeds = ["off", "low", "medium", "high"]
  def currentIndex = speeds.indexOf(currentSpeed)
  def nextIndex = (currentIndex + 1) % speeds.size()
  setSpeed(speeds[nextIndex])
}

def setDirection(direction) {
  log.info "Setting fan direction to ${direction} for ${device.displayName}"
  parent.sendHsCommand(id(), "fan-direction", [value: direction])
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
