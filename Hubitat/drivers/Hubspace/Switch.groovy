metadata {
  definition(name: "HubSpace Switch", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Refresh"
  }
}
def initialize() {}
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(device.deviceNetworkId - "hubspace-", "turn_on")  }
def off() { parent.sendHsCommand(device.deviceNetworkId - "hubspace-", "turn_off") }