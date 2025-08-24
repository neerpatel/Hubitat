/*
 * ====================================================================
 *  HubSpace Fan (Driver)
 *
 *  Capabilities: Switch, FanControl
 *  Purpose:
 *  - Map Hubitat fan actions to HubSpace: 'power', 'fan-speed', 'fan-direction'.
 *  - Converts Hubitat speed names to numeric values expected by HubSpace.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.2.1" }

metadata {
  definition(name: "HubSpace Fan", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer()) {
    capability "Initialize"
    capability "Switch"
    capability "FanControl"
    capability "Actuator"
    capability "Refresh"
    capability "PresenceSensor"
    
    command "setDirection", ["string"] // forward, reverse
    command "forward"
    command "reverse"

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
  preferences {
    input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
  }
}
def initialize() { log.debug "Initializing HubSpace Fan v${deviceVer()}" }
def updated() {
  try {
    if (settings?.devicePollSeconds) {
      device.updateDataValue("devicePollSeconds", String.valueOf((settings.devicePollSeconds as int)))
    } else {
      device.removeDataValue("devicePollSeconds")
    }
  } catch (ignored) {}
}

def refresh() { 
  parent.pollChild(device) 
}

def on() { 
  log.info "Turning on ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: "switch", value: "on")
  parent.sendHsCommand(id(), "power", [instance: "fan-power", value: "on"]) 
}

def off() { 
  log.info "Turning off ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: "switch", value: "off")
  parent.sendHsCommand(id(), "power", [instance: "fan-power", value: "off"]) 
}

def setSpeed(speed) {
  log.info "Setting fan speed to ${speed} for ${device.displayName} (drv v${deviceVer()})"
  
  // Determine device's max levels from data value (set by parent app during state updates)
  Integer maxLevels = (device.getDataValue('fanMaxLevels') ?: '6') as Integer

  // Map Hubitat speed name/number to a level index (1..maxLevels). 0 means off.
  Integer level = null
  def s = (speed instanceof String) ? speed?.toLowerCase() : speed
  switch (s) {
    case 'off': level = 0; break
    case 'low': level = 1; break
    case 'medium-low': level = Math.min(2, maxLevels); break
    case 'medium': level = Math.min(3, maxLevels); break
    case 'medium-high': level = Math.min(4, maxLevels); break
    case 'high': level = (maxLevels >= 5) ? 5 : maxLevels; break
    case 'on': level = maxLevels; break
    default:
      if (s instanceof Number || (s instanceof String && s.isNumber())) {
        level = (s as Integer)
      }
      break
  }
  if (level == null) { level = (maxLevels >= 3 ? 3 : 1) }

  if (level <= 0) {
    off()
    return
  }

  // Compute percent ladder and payload value "fan-speed-{maxLevels}-{percent}" (percent is zero-padded to 3 digits)
  int pct
  if (maxLevels >= 6) {
    int[] ladder = [16, 33, 50, 66, 83, 100]
    int idx = Math.max(1, Math.min(level, 6)) - 1
    pct = ladder[idx]
    maxLevels = 6
  } else { // assume 3-speed device
    int[] ladder = [33, 66, 100]
    int idx = Math.max(1, Math.min(level, 3)) - 1
    pct = ladder[idx]
    maxLevels = 3
  }
  String pctStr = String.format('%03d', pct)
  String apiValue = "fan-speed-${maxLevels}-${pctStr}"

  // Optimistic UI update
  String speedNameForUi
  switch ((speed instanceof String) ? speed.toLowerCase() : speed) {
    case 'off': speedNameForUi = 'off'; break
    case 'low': speedNameForUi = 'low'; break
    case 'medium-low': speedNameForUi = 'medium-low'; break
    case 'medium': speedNameForUi = 'medium'; break
    case 'medium-high': speedNameForUi = 'medium-high'; break
    case 'high': speedNameForUi = 'high'; break
    case 'on': speedNameForUi = 'on'; break
    default:
      def n = (speed as Integer)
      if (n <= 33) speedNameForUi = 'low'
      else if (n <= 66) speedNameForUi = 'medium'
      else speedNameForUi = 'high'
  }
  if (speedNameForUi) { sendEvent(name: 'speed', value: speedNameForUi) }

  // Ensure power on then set speed
  if (device.currentValue("switch") != "on") { on() }
  parent.sendHsCommand(id(), "fan-speed", [instance: "fan-speed", value: apiValue])
}

def cycleSpeed() {
  def currentSpeed = device.currentValue("speed") ?: "off"
  def speeds = ["off", "low", "medium", "high"]
  def currentIndex = speeds.indexOf(currentSpeed)
  def nextIndex = (currentIndex + 1) % speeds.size()
  setSpeed(speeds[nextIndex])
}

def setDirection(direction) {
  def d = (direction as String)?.toLowerCase()
  log.info "Setting fan direction to ${d} for ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: 'direction', value: d)
  // Many HubSpace fans use functionClass 'fan-reverse' with instance 'fan-reverse'
  parent.sendHsCommand(id(), "fan-reverse", [instance: "fan-reverse", value: d])
}

def forward() { setDirection('forward') }
def reverse() { setDirection('reverse') }

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
