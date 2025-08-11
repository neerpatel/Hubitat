metadata {
  definition(name: "HubSpace Light", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Switch Level"
    capability "ColorTemperature"
    capability "Refresh"
  }
  preferences {
    input name: "transitionMs", type: "number", title: "Fade (ms)", defaultValue: 300
  }
}
def initialize() {}
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(id(), "turn_on") }
def off() { parent.sendHsCommand(id(), "turn_off") }
def setLevel(v, dur=null) {
  parent.sendHsCommand(id(), "set_brightness", [level: v as int, transition: (dur?:transitionMs)])
}
def setColorTemperature(kelvin) {
  parent.sendHsCommand(id(), "set_color_temperature", [mireds: Math.max(153, Math.min(500, 1_000_000/(kelvin as int)))])
}
private id(){ device.deviceNetworkId - "hubspace-" }