metadata {
  definition(name: "HubSpace Light", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Switch Level"
    capability "ColorTemperature"
    capability "Color Control"
    capability "Refresh"
  }
  preferences {
    input name: "transitionMs", type: "number", title: "Fade (ms)", defaultValue: 300
  }
}
def initialize() {}
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(id(), "power", [value: "on"]) }
def off() { parent.sendHsCommand(id(), "power", [value: "off"]) }
def setLevel(v, dur=null) {
  parent.sendHsCommand(id(), "brightness", [value: v as int])
}
def setColorTemperature(kelvin) {
  parent.sendHsCommand(id(), "color-temperature", [value: kelvin as int])
}
def setColor(value) {
  // value is a map like [hue:0-99, saturation:0-99, colorName:"Red", hex:"#FF0000"]
  // Hubspace API expects RGB values (0-255)
  def rgb = hubitatColorToRgb(value)
  parent.sendHsCommand(id(), "color-rgb", [value: [r: rgb.r, g: rgb.g, b: rgb.b]])
}

private Map hubitatColorToRgb(Map color) {
  def hue = color.hue
  def saturation = color.saturation

  def h = hue * 3.6 // Convert to 0-360
  def s = saturation / 100.0 // Convert to 0-1
  def v = 1.0 // Assume full brightness for now

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

private id(){ device.deviceNetworkId - "hubspace-" }