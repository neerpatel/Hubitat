/*
 *  Hubspace Device Manager
 *  Provides connection to Hubspace cloud API and controls Hubspace devices.
 *  This driver is inspired by https://github.com/jdeath/Hubspace-Homeassistant
 *  and structured similar to drivers in https://github.com/DaveGut/HubitatActive.
 */


definition(
  name: "HubSpace Device Manager",
  namespace: "neerpatel/hubspace",
  author: "Neer Patel",
  importUrl: "https://raw.githubusercontent.com/neerpatel/hubspace/main/Hubitat/app/Hubspace/HubspaceDeviceManager.groovy", 
  description: "Discover and control HubSpace devices via cloud API",
  iconUrl: "",
  iconX2Url: "",
  installOnOpen: true,
  singleInstance: true
)


preferences {
  page(name: "mainPage")
  page(name: "addDevicesPage")
  page(name: "addDevStatus")
  page(name: "listDevices")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "HubSpace Device Manager", install: true, uninstall: true) {
    section("Bridge & Credentials") {
      input "nodeBridgeUrl", "text", title: "Bridge Server URL", description: "e.g., http://192.168.1.100:3000", required: true
      input "username", "text", title: "HubSpace Username/Email", required: true
      input "password", "password", title: "HubSpace Password", required: true
      if (state.nodeSessionId) {
        paragraph "✅ Connected to bridge"
        paragraph "Session: ${state.nodeSessionId}" 
        if (state.nodeBridgeAccountId) { paragraph "Account ID: ${state.nodeBridgeAccountId}" }
        input name: "testBridge", type: "button", title: "Test Bridge Connection"
        input name: "disconnectBridge", type: "button", title: "Disconnect Bridge"
      } else {
        input name: "connectBridge", type: "button", title: "Connect to Bridge"
      }
    }
    section("Health Monitoring") {
      input "healthSeconds", "number", title: "Health check interval (sec)", defaultValue: 120, required: true
      def h = state.bridgeHealth ?: [:]
      def stamp = h.last ? new Date(h.last) : 'never'
      def status = h.status ?: 'unknown'
      def extra = (h.status == 'ok') ? "uptime=${h.uptime}s sessions=${h.sessions} version=${h.version}" : (h.error ?: '')
      paragraph "Bridge Health: ${status} (${extra})\nLast checked: ${stamp}"
      input name: "checkHealth", type: "button", title: "Check Health Now"
    }
    section("Polling") {
      input "pollSeconds", "number", title: "Poll interval (sec)", defaultValue: 30, required: true
    }
    section("Actions") {
      input name: "discoverNow", type: "button", title: "Discover Devices Now"
      if (getChildDevices()?.size() > 0) {
        paragraph "Discovered devices: ${getChildDevices().size()}"
      }
    }
    section("Device Discovery & Add") {
      paragraph "Use the bridge to discover HubSpace devices, then select which to add."
      href "addDevicesPage", title: "Discover and Add Devices", description: "Scan via bridge and choose devices to install"
      href "listDevices", title: "List Discovered Devices", description: "Show discovered devices and install state"
    }
  }
}



// Handle app page buttons
void appButtonHandler(String btn) {
  if (btn == "discoverNow") {
    log.debug "HubSpace Bridge: manual discovery requested"
    refreshIndexAndDiscover()
  } else if (btn == "connectBridge") {
    log.debug "HubSpace Bridge: bridge connection requested"
    connectToNodeBridge()
  } else if (btn == "testBridge") {
    log.debug "HubSpace Bridge: bridge test requested"
    testNodeBridgeConnection()
  } else if (btn == "disconnectBridge") {
    log.debug "HubSpace Bridge: bridge disconnect requested"
    state.nodeSessionId = null
    log.info "Disconnected from Node.js bridge"
  } else if (btn == "checkHealth") {
    log.debug "HubSpace Bridge: health check requested"
    healthCheck(true)
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  log.debug "Initializing HubspaceDeviceManager"
  if (!state.nodeSessionId || !settings.nodeBridgeUrl) {
    log.info "Bridge not connected. Please configure URL and connect."
    return
  }
  
  // Seed discovery list on init so the Add Devices page has content
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
  // Health monitor
  schedule("*/${Math.max(30, (settings.healthSeconds ?: 120) as int)} * * * * ?", healthCheck)
}


private extractFromHtml(String html, String pattern) {
  def matcher = html =~ pattern
  return matcher ? matcher[0][1] : null
}

private extractAuthCode(String url) {
  def matcher = url =~ /code=([^&]*)/
  return matcher ? java.net.URLDecoder.decode(matcher[0][1], "UTF-8") : null
}

private discoverDevices() { refreshIndexAndDiscover() }

private connectToNodeBridge() {
  if (!settings.nodeBridgeUrl) {
    log.warn "Bridge URL not configured"
    return
  }
  
  if (!settings.username || !settings.password) {
    log.warn "HubSpace credentials required for bridge authentication"
    return
  }
  
  try {
    def loginData = [username: settings.username, password: settings.password]
    log.info "[NodeBridge] POST /login ${settings.nodeBridgeUrl}"
    httpPost([
      uri: "${settings.nodeBridgeUrl}/login",
      headers: ["Content-Type": "application/json"],
      contentType: 'application/json',
      body: groovy.json.JsonOutput.toJson(loginData),
      timeout: 15
    ]) { resp ->
      if (resp.status == 200) {
        def responseData = resp.data
        state.nodeSessionId = responseData.sessionId
        state.nodeBridgeAccountId = responseData.accountId
        log.info "[NodeBridge] Connected. session=${state.nodeSessionId} accountId=${state.nodeBridgeAccountId}"
      } else {
        throw new Exception("[NodeBridge] Login failed with status: ${resp.status}")
      }
    }
  } catch (Exception e) {
    log.error "[NodeBridge] Failed to connect: ${e.message}"
    state.nodeSessionId = null
  }
}

private testNodeBridgeConnection() {
  if (!state.nodeSessionId || !settings.nodeBridgeUrl) {
    log.warn "Bridge not connected or URL not configured"
    return
  }
  
  try {
    httpGet([
      uri: "${settings.nodeBridgeUrl}/devices",
      params: [session: state.nodeSessionId],
      timeout: 10
    ]) { resp ->
      if (resp.status == 200) {
        def deviceCount = resp.data?.size() ?: 0
        log.info "Bridge connection successful! Found ${deviceCount} devices."
      } else {
        throw new Exception("Bridge test failed with status: ${resp.status}")
      }
    }
  } catch (Exception e) {
    log.error "Bridge connection test failed: ${e.message}"
    state.nodeSessionId = null
  }
}


def healthCheck(force=false) {
  if (!settings.nodeBridgeUrl) return
  try {
    httpGet([
      uri: "${settings.nodeBridgeUrl}/health",
      timeout: 10
    ]) { resp ->
      def ok = (resp.status == 200 && resp.data?.status == 'ok')
      state.bridgeHealth = [
        status: ok ? 'ok' : 'error',
        uptime: resp.data?.uptime,
        sessions: resp.data?.sessions,
        version: resp.data?.version,
        last: now(),
        error: ok ? null : "status=${resp.status}"
      ]
      if (ok) {
        log.debug "[NodeBridge] Health OK uptime=${resp.data?.uptime}s sessions=${resp.data?.sessions}"
      } else {
        log.warn "[NodeBridge] Health check returned non-OK status=${resp.status}"
      }
    }
  } catch (Exception e) {
    state.bridgeHealth = [status: 'error', last: now(), error: e.message]
    if (force) {
      log.error "[NodeBridge] Health check failed: ${e.message}"
    } else {
      log.warn "[NodeBridge] Health check failed: ${e.message}"
    }
  }
}

def pollAll() {
  if (!state.nodeSessionId) {
    log.warn "Cannot poll devices - no Node bridge session"
    return
  }
  getChildDevices()?.each { c -> pollChild(c) }
}

def pollChild(cd) {
  def devId = cd.deviceNetworkId - "hubspace-"
  try {
    httpGet([
      uri: "${settings.nodeBridgeUrl}/state/${devId}",
      params: [session: state.nodeSessionId],
      timeout: 10
    ]) { resp ->
      updateFromState(cd, resp.data)
    }
  } catch (Exception e) {
    log.warn "Node bridge error polling device ${cd.displayName}: ${e.message}"
  }
}

String driverForType(String t) {
  switch(t) {
    case "light": return "HubSpace Light"
    case "switch": return "HubSpace Switch"
    case "fan": return "HubSpace Fan"
    case "ceiling-fan": return "HubSpace Fan"
    case "exhaust-fan": return "HubSpace Exhaust Fan"
    case "lock": return "HubSpace Lock"
    case "door-lock": return "HubSpace Lock"
    case "thermostat": return "HubSpace Thermostat"
    case "portable-air-conditioner": return "HubSpace Portable AC"
    case "valve": return "HubSpace Valve"
    case "water-timer": return "HubSpace Valve"
    case "security-system": return "HubSpace Security System"
    case "security-system-sensor": return "HubSpace Security System Sensor"
    default: return "HubSpace Device"
  }
}

// Called by child driver commands
def sendHsCommand(String devId, String cmd, Map args=[:]) {
  def values = [[functionClass: cmd, functionInstance: args.instance ?: null, value: args.value]]
  try {
    httpPost([
      uri: "${settings.nodeBridgeUrl}/command/${devId}",
      params: [session: state.nodeSessionId],
      headers: ["Content-Type": "application/json; charset=utf-8"],
      contentType: 'application/json',
      body: groovy.json.JsonOutput.toJson([values: values]),
      timeout: 10
    ]) { resp ->
      if (resp.status != 200) {
        log.warn "Node bridge command failed: $cmd $args -> ${resp.data}"
      } else {
        log.debug "Node bridge command successful: $cmd $args"
        def childDevice = getChildDevices().find { it.deviceNetworkId == "hubspace-${devId}" }
        if (childDevice) {
          runIn(2, "pollChild", [data: childDevice])
        }
      }
    }
  } catch (Exception e) {
    log.error "Node bridge error sending command $cmd to device $devId: ${e.message}"
  }
}



void refreshIndexAndDiscover() {
  // Maintain a discovery cache similar to Kasa app's state.devices
  Map<String, Map> disc = state.devices ?: [:]

  // Get all devices from Hubspace via Node bridge
  List allDevices = []
  try {
    log.info "[NodeBridge] GET /devices"
    httpGet([
      uri: "${settings.nodeBridgeUrl}/devices",
      params: [session: state.nodeSessionId],
      timeout: 15
    ]) { resp ->
      log.debug "[NodeBridge] devices status=${resp.status} count=${resp?.data?.size()}"
      allDevices = (resp?.data as List) ?: []
    }
  } catch (Throwable t) {
    log.warn "Failed to retrieve devices from Hubspace API: ${t?.message}"
    return
  }

  // Normalize and cache
  allDevices.each { Map d ->
    String typeId = (d.typeId ?: d.type)?.toString()
    if (typeId && typeId != 'metadevice.device') { return }

    String id = (d.id ?: d.deviceId ?: d.metadeviceId ?: d.device_id)?.toString()
    String type = (
      d.device_class ?: d?.description?.device?.deviceClass ?: d?.description?.deviceClass
    )?.toString()?.toLowerCase()
    String name = (
      d.friendlyName ?: d.friendly_name ?: d?.description?.device?.friendlyName ?: d.default_name ?: (id ? "HubSpace ${id}" : null)
    )?.toString()
    if (!id || !type) { return }

    String dni = "hubspace-${id}"
    disc[dni] = [
      dni: dni,
      id: id,
      type: type,
      name: name ?: dni,
      raw: d
    ]
  }

  state.devices = disc
}

// ===== Kasa-style Add Devices Flow (adapted for HubSpace bridge) =====
def addDevicesPage() {
  log.debug "addDevicesPage: begin"
  // Always refresh discovery cache on entry
  refreshIndexAndDiscover()

  Map devices = state.devices ?: [:]
  Map uninstalled = [:]
  devices.each { k, v ->
    def child = getChildDevice(k)
    if (!child) {
      uninstalled[k] = "${v.name ?: v.id}, ${v.type}"
    }
  }

  return dynamicPage(name: "addDevicesPage",
                     title: "Add HubSpace Devices to Hubitat",
                     nextPage: "addDevStatus",
                     install: false) {
    section() {
      paragraph "Select devices to add. This page refreshes each time you open it."
      input(name: "selectedAddDevices", type: "enum", title: "Devices to add (${uninstalled.size() ?: 0} available)",
            multiple: true, required: false, options: uninstalled)
    }
  }
}

def addDevStatus() {
  addDevices()
  def addMsg = new StringBuilder()
  def failMsg = new StringBuilder()
  if (!state.addedDevices) {
    addMsg << "Added Devices: No devices added."
  } else {
    addMsg << "<b>The following devices were installed:</b>\n"
    state.addedDevices.each { addMsg << "\t${it}\n" }
  }
  if (state.failedAdds) {
    failMsg << "<b>The following devices were not installed:</b>\n"
    state.failedAdds.each { failMsg << "\t${it}\n" }
  }
  return dynamicPage(name: "addDevStatus",
                     title: "Installation Status",
                     nextPage: "listDevices",
                     install: false) {
    section() {
      paragraph addMsg.toString()
      paragraph failMsg.toString()
    }
  }
}

def addDevices() {
  log.info "addDevices: selected=${settings.selectedAddDevices}"
  state.addedDevices = []
  state.failedAdds = []
  def devices = state.devices ?: [:]
  (settings.selectedAddDevices ?: []).each { String dni ->
    def child = getChildDevice(dni)
    if (child) { return }
    def rec = devices[dni]
    if (!rec) { state.failedAdds << [dni: dni, reason: 'not in discovery']; return }
    try {
      String driver = driverForType(rec.type as String)
      def added = addChildDevice(
        "neerpatel/hubspace",
        driver,
        dni,
        [label: rec.name ?: dni, isComponent: false]
      )
      added?.updateDataValue("hsType", rec.type as String)
      state.addedDevices << [label: rec.name, id: rec.id]
      log.info "Installed ${rec.name} (${rec.type})"
    } catch (Throwable t) {
      state.failedAdds << [label: rec?.name, driver: rec?.type, id: rec?.id, error: t?.message]
      log.warn "Failed to add ${rec?.name}: ${t?.message}"
    }
    pauseExecution(250)
  }
  app?.removeSetting("selectedAddDevices")
}

def listDevices() {
  log.debug "listDevices"
  Map devices = state.devices ?: [:]
  List lines = []
  devices.keySet().sort().each { String dni ->
    def rec = devices[dni]
    def installed = getChildDevice(dni) ? 'Yes' : 'No'
    lines << "<b>${rec.name} - ${rec.type}</b>: [id: ${rec.id}, installed: ${installed}]"
  }
  return dynamicPage(name: "listDevices",
                     title: "Discovered HubSpace Devices",
                     nextPage: "mainPage",
                     install: false) {
    section() {
      paragraph "<b>Total HubSpace devices: ${devices.size() ?: 0}</b>\n<b>Alias: [id, Installed?]</b>"
      paragraph "<p style='font-size:14px'>${lines.join('\n')}</p>"
    }
  }
}

private updateFromState(cd, Map deviceData) {
  // Normalize various response shapes from the bridge
  if (!deviceData) return

  // Common shapes observed: { states: [...] }, { values: [...] }, or flat map of fc->value
  def listStates = null
  if (deviceData.states instanceof List) {
    listStates = deviceData.states
  } else if (deviceData.values instanceof List) {
    listStates = deviceData.values
  }

  if (listStates instanceof List) {
    listStates.each { st ->
      if (st instanceof Map) {
        processStateValue(cd, st.functionClass as String, st.functionInstance as String, st.value)
      }
    }
    return
  }

  // Fallback: treat entries as key/value pairs but ignore non-state metadata
  def ignoreKeys = [
    'id','metadeviceId','name','type','friendlyName','friendly_name','device_class','description',
    'lastUpdateTime','accountId','deviceId','device_id','typeId'
  ] as Set
  deviceData.each { k, v ->
    if (!ignoreKeys.contains(k as String)) {
      processStateValue(cd, k as String, null, v)
    }
  }
}

private processStateValue(cd, String functionClass, String functionInstance, value) {
  switch (functionClass) {
    case "values":
      // Already handled at caller; ignore to avoid log noise
      break
    case "power":
      cd.sendEvent(name: "switch", value: value == "on" ? "on" : "off")
      break
    case "brightness":
      cd.sendEvent(name: "level", value: (value as int))
      break
    case "color-temperature":
      cd.sendEvent(name: "colorTemperature", value: (value as int))
      break
    case "color-mode":
      // Map HubSpace color modes to Hubitat colorMode values
      def cm = (value as String)
      def hubitatMode = (cm == 'color') ? 'RGB' : ((cm == 'white') ? 'CT' : cm)
      cd.sendEvent(name: "colorMode", value: hubitatMode)
      break
    case "color-rgb":
      if (value instanceof Map) {
        def rgb = value
        def hsv = rgbToHSV(rgb.r as int, rgb.g as int, rgb.b as int)
        cd.sendEvent(name: "hue", value: hsv.h)
        cd.sendEvent(name: "saturation", value: hsv.s)
      }
      break
    case "fan-speed":
      if (functionInstance == "ac-fan-speed") {
        cd.sendEvent(name: "thermostatFanMode", value: value as String)
      } else {
        cd.sendEvent(name: "speed", value: (value as String))
      }
      break
    case "fan-direction":
      cd.sendEvent(name: "direction", value: value as String)
      break
    case "lock":
      cd.sendEvent(name: "lock", value: value == "locked" ? "locked" : "unlocked")
      break
    case "motion-detection":
      cd.sendEvent(name: "motion", value: value == "motion-detected" ? "active" : "inactive")
      break
    case "humidity-threshold-met":
      cd.sendEvent(name: "humidity", value: value == "above-threshold" ? "active" : "inactive")
      break
    case "auto-off-timer":
      cd.sendEvent(name: "autoOffTimer", value: value as int)
      break
    case "timer-duration":
      // Water timer duration setting
      cd.sendEvent(name: "duration", value: value as int)
      break
    case "motion-action":
      cd.sendEvent(name: "motionAction", value: value as String)
      break
    case "sensitivity":
      cd.sendEvent(name: "sensitivity", value: value as String)
      break
    case "alarm-status":
      // Raise both custom and standard Alarm capability events
      cd.sendEvent(name: "alarmStatus", value: value as String)
      cd.sendEvent(name: "alarm", value: value as String)
      break
    case "temperature":
      if (functionInstance == "current-temp") {
        cd.sendEvent(name: "temperature", value: value as float)
      } else if (functionInstance == "cooling-target") {
        cd.sendEvent(name: "coolingSetpoint", value: value as float)
      } else if (functionInstance == "heating-target") {
        cd.sendEvent(name: "heatingSetpoint", value: value as float)
      }
      break
    case "mode":
      cd.sendEvent(name: "thermostatMode", value: value as String)
      break
    case "sleep":
      cd.sendEvent(name: "sleepMode", value: value as String)
      break
    case "available":
      cd.sendEvent(name: "presence", value: value ? "present" : "not present")
      break
    case "visible":
      cd.sendEvent(name: "visible", value: value as String)
      break
    case "direct":
      cd.sendEvent(name: "direct", value: value as String)
      break
    case "wifi-ssid":
      cd.sendEvent(name: "ssid", value: value as String)
      break
    case "wifi-rssi":
      try { cd.sendEvent(name: "rssi", value: (value as int)) } catch (ignored) { cd.sendEvent(name: "rssi", value: value as String) }
      break
    case "wifi-steady-state":
      cd.sendEvent(name: "wifiState", value: value as String)
      break
    case "wifi-setup-state":
      cd.sendEvent(name: "wifiSetupState", value: value as String)
      break
    case "wifi-mac-address":
      cd.sendEvent(name: "wifiMac", value: value as String)
      break
    case "geo-coordinates":
      def lat = null; def lon = null
      try {
        def gc = value?.get('geo-coordinates') ?: value
        lat = (gc?.latitude as BigDecimal)
        lon = (gc?.longitude as BigDecimal)
      } catch (ignored) {}
      if (lat != null && lon != null) {
        cd.sendEvent(name: "latitude", value: lat)
        cd.sendEvent(name: "longitude", value: lon)
        cd.sendEvent(name: "location", value: "${lat},${lon}")
      } else {
        cd.sendEvent(name: "location", value: value?.toString())
      }
      break
    case "scheduler-flags":
      cd.sendEvent(name: "schedulerFlags", value: value as String)
      break
    case "error-flag":
      // Emit a per-instance boolean flag and an aggregate status
      def inst = (functionInstance ?: 'error')?.toString().replace('-', '_')
      cd.sendEvent(name: inst, value: (value?.toString()))
      // Optional: if any error flag is true, set healthStatus = error
      if (value == true || value?.toString() == 'true') {
        cd.sendEvent(name: 'healthStatus', value: 'error')
      }
      break
    case "battery-level":
      cd.sendEvent(name: "battery", value: value as int)
      break
    // Handle switches and power outlets
    case "switch":
      cd.sendEvent(name: "switch", value: value ? "on" : "off")
      break
    // Add more cases for other device types and attributes as needed
    default:
      log.debug "Unhandled state: ${functionClass}/${functionInstance} - ${value}"
      break
  }
}

// Helper function to convert RGB to HSV
private Map rgbToHSV(int r, int g, int b) {
  float rf = r / 255.0
  float gf = g / 255.0
  float bf = b / 255.0
  
  float max = Math.max(rf, Math.max(gf, bf))
  float min = Math.min(rf, Math.min(gf, bf))
  float delta = max - min
  
  float h = 0, s = 0, v = max
  
  if (delta != 0) {
    s = delta / max
    if (max == rf) {
      h = ((gf - bf) / delta) % 6
    } else if (max == gf) {
      h = (bf - rf) / delta + 2
    } else {
      h = (rf - gf) / delta + 4
    }
    h *= 60
    if (h < 0) h += 360
  }
  
  return [h: Math.round(h * 100 / 360), s: Math.round(s * 100), v: Math.round(v * 100)]
}
