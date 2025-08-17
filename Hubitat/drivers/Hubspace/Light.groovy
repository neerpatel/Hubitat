metadata {
  definition(name: "HubSpace Light", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Switch Level"
    capability "ColorTemperature"
    capability "Color Control"
    capability "Refresh"
    capability "PresenceSensor"
  }
  preferences {
    input name: "transitionMs", type: "number", title: "Fade (ms)", defaultValue: 300
  }
}

def initialize() {
    log.debug "Initializing HubSpace Light"
    refresh()
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

def setLevel(v, dur=null) {
  log.info "Setting level to ${v} for ${device.displayName}"
  parent.sendHsCommand(id(), "brightness", [value: v as int])
}

def setColorTemperature(kelvin) {
  log.info "Setting color temperature to ${kelvin}K for ${device.displayName}"
  parent.sendHsCommand(id(), "color-temperature", [value: kelvin as int])
}

def setColor(value) {
  log.info "Setting color for ${device.displayName}: ${value}"
  // value is a map like [hue:0-99, saturation:0-99, level:0-100]
  def rgb = hubitatColorToRgb(value)
  parent.sendHsCommand(id(), "color-rgb", [value: [r: rgb.r, g: rgb.g, b: rgb.b]])
  
  // Also set level if provided
  if (value.level != null) {
    setLevel(value.level)
  }
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
