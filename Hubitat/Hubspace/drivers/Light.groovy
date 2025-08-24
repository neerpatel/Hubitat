/*
 * ====================================================================
 *  HubSpace Light (Driver)
 *
 *  Capabilities: Switch, Switch Level, ColorTemperature, Color Control
 *  Purpose:
 *  - Map Hubitat lighting commands to HubSpace function classes via parent app.
 *  - Functions used: power, brightness, color-temperature, color-rgb.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Color input is Hubitat HSV; converted to RGB for HubSpace.
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String deviceVer() { return "0.1.2" }

metadata {
  definition(name: "HubSpace Light", namespace: "neerpatel/hubspace", author: "Neer Patel", version: deviceVer()) {
    capability "Initialize"
    capability "Switch"
    capability "Switch Level"
    capability "ColorTemperature"
    capability "Color Control"
    capability "Actuator"
    capability "Refresh"
    capability "PresenceSensor"

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
    input name: "transitionMs", type: "number", title: "Fade (ms)", defaultValue: 300
    input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
  }
}

def initialize() { log.debug "Initializing HubSpace Light v${deviceVer()}" }
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

def on() { 
  log.info "Turning on ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: "switch", value: "on")
  parent.sendHsCommand(id(), "power", [instance: "light-power", value: "on"]) 
}

def off() { 
  log.info "Turning off ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: "switch", value: "off")
  parent.sendHsCommand(id(), "power", [instance: "light-power", value: "off"]) 
}

def setLevel(v, dur=null) {
  log.info "Setting level to ${v} for ${device.displayName} (drv v${deviceVer()})"
  def lvl = (v as int)
  sendEvent(name: "level", value: lvl)
  if (lvl > 0) { sendEvent(name: "switch", value: "on") }
  parent.sendHsCommand(id(), "brightness", [value: lvl])
}

def setColorTemperature(kelvin) {
  log.info "Setting color temperature to ${kelvin}K for ${device.displayName} (drv v${deviceVer()})"
  sendEvent(name: "colorTemperature", value: (kelvin as int))
  parent.sendHsCommand(id(), "color-temperature", [value: kelvin as int])
}

def setColor(value) {
  log.info "Setting color for ${device.displayName} (drv v${deviceVer()}): ${value}"
  // value is a map like [hue:0-99, saturation:0-99, level:0-100]
  def supports = device.getDataValue('supportsColor') == 'true'
  if (!supports) {
    log.warn "${device.displayName} does not report color support; skipping setColor"
    return
  }
  def rgb = hubitatColorToRgb(value)
  parent.sendHsCommand(id(), "color-rgb", [value: [r: rgb.r, g: rgb.g, b: rgb.b]])
  
  // Also set level if provided
  if (value.level != null) {
    setLevel(value.level)
  }
  if (value.hue != null) { sendEvent(name: "hue", value: (value.hue as int)) }
  if (value.saturation != null) { sendEvent(name: "saturation", value: (value.saturation as int)) }
}

def setHue(hue) {
  def currentColor = [hue: hue, saturation: device.currentValue("saturation") ?: 100]
  setColor(currentColor)
}

def setSaturation(saturation) {
  def currentColor = [hue: device.currentValue("hue") ?: 0, saturation: saturation]
  setColor(currentColor)
}

private Map hubitatColorToRgb(Map color) {
  def hue = color.hue ?: 0
  def saturation = color.saturation ?: 100
  def level = color.level ?: device.currentValue("level") ?: 100

  def h = hue * 3.6 // Convert to 0-360
  def s = saturation / 100.0 // Convert to 0-1
  def v = level / 100.0 // Convert to 0-1

  def r, g, b

  def i = Math.floor(h / 60)
  def f = h / 60 - i
  def p = v * (1 - s)
  def q = v * (1 - f * s)
  def t = v * (1 - (1 - f) * s)

  switch (i) {
    case 0: r = v; g = t; b = p; break
    case 1: r = q; g = v; b = p; break
    case 2: r = p; g = v; b = t; break
    case 3: r = p; g = q; b = v; break
    case 4: r = t; g = p; b = v; break
    case 5: r = v; g = p; b = q; break
  }

  return [
    r: Math.round(r * 255),
    g: Math.round(g * 255),
    b: Math.round(b * 255)
  ]
}

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
