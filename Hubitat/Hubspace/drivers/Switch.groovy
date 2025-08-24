/*
 * ====================================================================
 *  HubSpace Switch (Driver)
 *
 *  Capabilities: Switch
 *  Purpose:
 *  - Map Hubitat on/off to HubSpace function class 'power' via parent app.
 *  - Versioned logging via driverVer() for diagnostics.
 *
 *  Notes:
 *  - Telemetry attributes (wifi, rssi, etc.) are populated by the parent app.
 * ====================================================================
 */

String driverVer() { return "0.1.0" }

metadata {
  definition(name: "HubSpace Switch", namespace: "neerpatel/hubspace", author: "Neer Patel", version: "0.1.0") {
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
  preferences {
    input name: "devicePollSeconds", type: "number", title: "Device refresh interval (sec)", description: "Override app polling for this device", required: false
  }
}
def initialize() { log.debug "Initializing HubSpace Switch v${driverVer()}" }
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
def on()  { log.info "Switch on (drv v${driverVer()}) ${device.displayName}"; parent.sendHsCommand(id(), "power", [value: "on"]) }
def off() { log.info "Switch off (drv v${driverVer()}) ${device.displayName}"; parent.sendHsCommand(id(), "power", [value: "off"]) }

private id() { device.deviceNetworkId - "hubspace-" }
