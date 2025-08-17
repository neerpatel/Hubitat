metadata {
  definition(name: "HubSpace Switch", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Refresh"
  }
}
def initialize() {}
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(id(), "power", [value: "on"])  }
def off() { parent.sendHsCommand(id(), "power", [value: "off"]) }

private id() { device.deviceNetworkId - "hubspace-" }
