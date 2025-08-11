/*
 *  Hubspace Device Manager
 *  Provides connection to Hubspace cloud API and controls Hubspace devices.
 *  This driver is inspired by https://github.com/jdeath/Hubspace-Homeassistant
 *  and structured similar to drivers in https://github.com/DaveGut/HubitatActive.
 */

definition(
  name: "HubSpace Bridge",
  namespace: "neerpatel/hubspace",
  author: "Neer Patel",
  description: "Discover and control HubSpace devices via local bridge",
  singleInstance: true
)


preferences {
  page(name: "mainPage")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "HubSpace Bridge", install: true, uninstall: true) {
    section("Bridge") {
      input "bridgeUrl", "text", title: "Bridge URL (e.g. http://pi:8123)", required: true
    }
    section("HubSpace Login (stored on bridge)") {
      input "hsUser", "text", title: "Email", required: true
      input "hsPass", "password", title: "Password", required: true
    }
    section("Polling") {
      input "pollSeconds", "number", title: "Poll interval (sec)", defaultValue: 30, required: true
    }
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  loginToBridge()
  discoverDevices()
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
}

private loginToBridge() {
  httpPostJson([uri: "${bridgeUrl}/login",
                body: [username: hsUser, password: hsPass],
                timeout: 10]) { resp -> log.debug "Bridge login: ${resp.data}" }
}

private discoverDevices() {
  httpGet([uri: "${bridgeUrl}/devices", timeout: 10]) { resp ->
    resp.data.each { d ->
      def dni = "hubspace-${d.id}"
      def typeName = driverForType(d.type)   // map to child driver name
      def child = getChildDevice(dni) ?: addChildDevice("neer/hubspace", typeName, dni,
                      [label: d.name, isComponent: false])
      child?.sendEvent(name: "deviceNetworkId", value: d.id)
      child?.updateDataValue("hsType", d.type as String)
    }
  }
}

def pollAll() {
  getChildDevices()?.each { c -> pollChild(c) }
}

def pollChild(cd) {
  def devId = cd.deviceNetworkId - "hubspace-"
  httpGet([uri: "${bridgeUrl}/state/${devId}", timeout: 10]) { resp ->
    updateFromState(cd, resp.data)
  }
}

private updateFromState(cd, Map state) {
  // normalize into Hubitat attributes (switch, level, colorTemperature, fanSpeed, lock, thermostatOperatingState...)
  if(state.switch != null) cd.sendEvent(name:"switch", value: state.switch ? "on" : "off")
  if(state.brightness != null) cd.sendEvent(name:"level", value: (state.brightness as int))
  if(state.color_temp != null) cd.sendEvent(name:"colorTemperature", value: (state.color_temp as int))
  if(state.fan_speed != null) cd.sendEvent(name:"speed", value: (state.fan_speed as String))
  if(state.lock != null) cd.sendEvent(name:"lock", value: state.lock ? "locked" : "unlocked")
  // ...extend per device type
}

String driverForType(String t) {
  switch(t) {
    case "light": return "HubSpace Light (LAN)"
    case "switch": return "HubSpace Switch (LAN)"
    case "fan": return "HubSpace Fan (LAN)"
    case "lock": return "HubSpace Lock (LAN)"
    case "thermostat": return "HubSpace Thermostat (LAN)"
    case "valve": return "HubSpace Valve (LAN)"
    default: return "HubSpace Device (LAN)"
  }
}

// Called by child driver commands
def sendHsCommand(String devId, String cmd, Map args=[:]) {
  httpPostJson([uri: "${bridgeUrl}/command/${devId}", body: [cmd: cmd, args: args], timeout: 10]) { resp ->
    if(!resp.data?.ok) log.warn "Command failed: $cmd $args -> ${resp.data}"
  }
}