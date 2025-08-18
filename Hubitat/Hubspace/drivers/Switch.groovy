metadata {
  definition(name: "HubSpace Switch", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Switch"
    capability "Actuator"
    capability "Refresh"

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
def initialize() {}
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(id(), "power", [value: "on"])  }
def off() { parent.sendHsCommand(id(), "power", [value: "off"]) }

private id() { device.deviceNetworkId - "hubspace-" }
