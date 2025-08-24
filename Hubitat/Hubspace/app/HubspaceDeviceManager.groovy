/*
 * ====================================================================
 *  HubSpace Device Manager (App)
 *
 *  Purpose:
 *  - Connects Hubitat to HubSpace via the local Node bridge (bridge-node).
 *  - Manages login/session, discovery of devices, child creation, polling,
 *    and routing commands from children to HubSpace function classes.
 *
 *  Key Features:
 *  - Preferences for bridge URL and HubSpace credentials (username/password).
 *  - Connects to bridge: POST /login; stores session/accountId.
 *  - Discovery: GET /devices; normalizes list; add selected as child devices.
 *  - Polling: GET /state/{id}; translates function states to Hubitat events.
 *  - Commands: PUT /command/{id}; maps Hubitat actions to HubSpace functions via sendHsCommand.
 *  - Health: /health periodic checks with status in app page.
 *  - Versioned logging: appVersion() is included in key log points.
 *
 *  Notes:
 *  - No credentials are logged. Session ID is shown for diagnostics only.
 *  - Function class mapping/event translation lives in updateFromState/processStateValue.
 *  - Drivers call parent.sendHsCommand to hit the bridge.
 * ====================================================================
 */

// Version helper (Kasa-style): include in logs and diagnostics
String appVersion() { return "0.2.2" }


definition(
  name: "HubSpace Device Manager",
  namespace: "neerpatel/hubspace",
  author: "Neer Patel",
  version: appVersion(),
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
  page(name: "removeDevicesPage")
  page(name: "removeDevStatus")
  page(name: "uninstalled")
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
      href "removeDevicesPage", title: "Remove Installed Devices", description: "Select and remove HubSpace child devices"
    }
    section("Device Discovery & Add") {
      paragraph "Use the bridge to discover HubSpace devices, then select which to add."
      href "addDevicesPage", title: "Discover and Add Devices", description: "Scan via bridge and choose devices to install"
      href "listDevices", title: "List Discovered Devices", description: "Show discovered devices and install state"
    }
    section("Uninstall") {
      paragraph "Uninstall all the devices"  
      href "uninstalled", title: "Uninstall all Devices", description: "Select and remove HubSpace child devices"
    }
  }
}

def uninstalled() {
  List lines = []
  log.info "Uninstalling all HubSpace devices"
  def childDevice = getChildDevices().find { it.deviceNetworkId == "hubspace-${devId}" }
  log.info "Found child device: ${childDevice.size()}"
  getChildDevices().each {
        log.info "Attempting to remove child device ${it.deviceNetworkId}"
        lines << "<p style='font-size:14px'>${it.deviceNetworkId}</p>"
        log.info "Removing child device ${it.deviceNetworkId}"
        //deleteChildDevice(it.deviceNetworkId)
        lines << "<p style='font-size:14px'>${it.deviceNetworkId}</p>"
        log.info "Removed child device ${it.deviceNetworkId}"
      }
      lines << "<p style='font-size:14px'>All child devices removed.</p>"
  return dynamicPage(name: "uninstalled", title: "Uninstalling Devices", install: false) {
    section("Removing all child devices...") {
     
      paragraph "<p style='font-size:14px'>${lines.join('\n')}</p>"
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
  log.debug "Initializing HubspaceDeviceManager v${appVersion()}"
  if (!state.nodeSessionId || !settings.nodeBridgeUrl) {
    log.info "Bridge not connected (app v${appVersion()}). Configure URL and connect."
    return
  }
  
  // Seed discovery list on init so the Add Devices page has content
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  if (state.lastPolled == null) state.lastPolled = [:]
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
    log.warn "HubSpace credentials required for bridge authentication (app v${appVersion()})"
    return
  }
  
  try {
    def loginData = [username: settings.username, password: settings.password]
    log.info "[NodeBridge] POST /login ${settings.nodeBridgeUrl} (app v${appVersion()})"
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
        log.info "[NodeBridge] Connected (app v${appVersion()}). session=${state.nodeSessionId} accountId=${state.nodeBridgeAccountId}"
      } else {
        throw new Exception("[NodeBridge] Login failed (app v${appVersion()}) with status: ${resp.status}")
      }
    }
  } catch (Exception e) {
    log.error "[NodeBridge] Failed to connect (app v${appVersion()}): ${e.message}"
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
        log.info "Bridge connection successful (app v${appVersion()})! Found ${deviceCount} devices."
      } else {
        throw new Exception("Bridge test failed (app v${appVersion()}) with status: ${resp.status}")
      }
    }
  } catch (Exception e) {
    log.error "Bridge connection test failed (app v${appVersion()}): ${e.message}"
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
        log.debug "[NodeBridge] Health OK (app v${appVersion()}) uptime=${resp.data?.uptime}s sessions=${resp.data?.sessions}"
      } else {
        log.warn "[NodeBridge] Health check returned non-OK (app v${appVersion()}) status=${resp.status}"
      }
    }
  } catch (Exception e) {
    state.bridgeHealth = [status: 'error', last: now(), error: e.message]
    if (force) {
      log.error "[NodeBridge] Health check failed (app v${appVersion()}): ${e.message}"
    } else {
      log.warn "[NodeBridge] Health check failed (app v${appVersion()}): ${e.message}"
    }
  }
}

def pollAll() {
  if (!state.nodeSessionId) {
    log.warn "Cannot poll devices - no Node bridge session"
    return
  }
  if (state.lastPolled == null) state.lastPolled = [:]
  Long nowMs = now()
  Integer defaultSec = ((settings.pollSeconds ?: 30) as int)
  getChildDevices()?.each { cd ->
    Integer devSec = null
    try {
      def v = cd.getDataValue('devicePollSeconds')
      if (v && v.isInteger()) { devSec = (v as Integer) }
    } catch (ignored) {}
    int interval = (devSec ?: defaultSec)
    Long last = (state.lastPolled[cd.deviceNetworkId] ?: 0L) as Long
    if (last == 0L || (nowMs - last) >= (interval * 1000L)) {
      pollChild(cd)
      state.lastPolled[cd.deviceNetworkId] = nowMs
    }
  }
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
    log.warn "Node bridge error polling device ${cd.displayName} (app v${appVersion()}): ${e.message}"
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
        log.warn "Node bridge command failed (app v${appVersion()}): $cmd $args -> ${resp.data}"
      } else {
        log.debug "Node bridge command successful (app v${appVersion()}): $cmd $args"
        def childDevice = getChildDevices().find { it.deviceNetworkId == "hubspace-${devId}" }
        if (childDevice) {
          runIn(2, "pollChild", [data: childDevice])
        }
      }
    }
  } catch (Exception e) {
    log.error "Node bridge error sending command $cmd to device $devId (app v${appVersion()}): ${e.message}"
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
    String deviceId = (d.deviceId ?: d.device_id)?.toString()
    List children = []
    try {
      if (d.children instanceof List) {
        children = d.children.collect { it?.toString() }.findAll { it }
      }
    } catch (ignored) {}
    if (!id || !type) { return }

    String dni = "hubspace-${id}"
    disc[dni] = [
      dni: dni,
      id: id,
      type: type,
      name: name ?: dni,
      deviceId: deviceId,
      children: children,
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
  // Group by physical deviceId when available to identify parent/child sets
  Map<String, List<Map>> byDevId = [:].withDefault { [] }
  devices.each { String k, Map v ->
    String devId = (v.deviceId ?: '') as String
    if (devId) {
      byDevId[devId] << v
    }
  }
  // Build list of parent/standalone DNIs to show
  Set<String> showDnis = [] as Set
  if (byDevId) {
    byDevId.each { String physId, List<Map> group ->
      if (group.size() > 1) {
        // Prefer explicit parent with children; fallback to class 'ceiling-fan'
        Map parent = group.find { (it.children instanceof List) && it.children.size() > 0 }
        if (!parent) { parent = group.find { (it.type as String) == 'ceiling-fan' } }
        if (!parent) { parent = group[0] }
        showDnis << (parent.dni as String)
      } else {
        showDnis << (group[0].dni as String)
      }
    }
  } else {
    // Fallback to children-based filtering if deviceId not provided
    Set childDnis = [] as Set
    devices.each { String k, Map v -> (v.children ?: []).each { String cid -> childDnis << "hubspace-${cid}" } }
    devices.each { String k, Map v -> if (!childDnis.contains(k)) { showDnis << k } }
  }
  showDnis.each { String k ->
    def v = devices[k]
    if (v && !getChildDevice(k)) {
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

// ===== Remove Devices Flow =====
def removeDevicesPage() {
  log.debug "removeDevicesPage: begin"
  Map devices = state.devices ?: [:]
  def installedDevices = [:]
  devices.keySet().sort().each { String dni ->
    def rec = devices[dni]
    def installed = getChildDevice(dni) ? 'Yes' : 'No'
    installedDevices[dni] = "${rec.name} - ${rec.type} [id: ${rec.id}, installed: ${installed}]"
  }
	
  // getChildDevices()?.each { cd -> installed[cd.deviceNetworkId] = cd.displayName }
  return dynamicPage(name: "removeDevicesPage",
                     title: "Remove HubSpace Devices from Hubitat",
                     nextPage: "removeDevStatus",
                     install: false) {
    section("Select Devices to Remove from Hubitat") {
      if (!installedDevices) {
        paragraph "No installed HubSpace devices found."
      } else {
        paragraph "Select devices to remove. This will delete the child device(s) from Hubitat."
        input(
          name: "selectedRemoveDevices", 
          type: "enum", 
          title: "Devices to remove (${installedDevices.size()})",
          multiple: true, 
          required: false, 
          options: installedDevices)
      }
    }
  }
}

def removeDevStatus() {
  removeSelectedDevices()
  def ok = state.removedDevices ?: []
  def fail = state.failedRemoves ?: []
  def okMsg = new StringBuilder()
  def failMsg = new StringBuilder()
  if (ok) {
    okMsg << "<b>The following devices were removed:</b>\n"
    ok.each { okMsg << "\t${it}\n" }
  } else {
    okMsg << "No devices were removed."
  }
  if (fail) {
    failMsg << "<b>Failed to remove:</b>\n"
    fail.each { m -> failMsg << "\t${m}\n" }
  }
  return dynamicPage(name: "removeDevStatus",
                     title: "Removal Status",
                     nextPage: "mainPage",
                     install: false) {
    section() {
      paragraph okMsg.toString()
      if (fail) { paragraph failMsg.toString() }
    }
  }
}

private void removeSelectedDevices() {
  state.removedDevices = []
  state.failedRemoves = []
  def picks = settings.selectedRemoveDevices ?: []
  if (!picks) return
  picks.each { String dni ->
    try {
      log.info("Attempting to remove child device ${dni}")
      def cd = getChildDevice(dni)
      log.info("Attempting to remove child device ${cd}")
      if (cd) {
        def label = cd.displayName
        deleteChildDevice(dni)
        state.removedDevices << label
        log.info "Removed child device ${label} (${dni})"
      } else {
        state.failedRemoves << "${dni} (not found)"
      }
    } catch (Throwable t) {
      state.failedRemoves << "${dni}: ${t?.message}"
      log.warn "Failed to remove child device ${dni}: ${t?.message}"
    }
    pauseExecution(150)
  }
  app?.removeSetting("selectedRemoveDevices")
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
      List created = []
      List children = (rec.children instanceof List) ? rec.children : []
      // If children are not present, derive from other metadevices with the same physical deviceId
      if (!children || children.size() == 0) {
        String physId = rec.deviceId as String
        if (physId) {
          devices.each { String dk, Map dv ->
            if (dv.deviceId == physId && dv.id != rec.id) {
              children << (dv.id as String)
            }
          }
        }
      }
      if (children && children.size() > 0) {
        // Create a Hubitat device for each child metadevice under this parent
        children.each { String cid ->
          String cdni = "hubspace-${cid}"
          def crec = devices[cdni]
          if (!crec) { return }
          if (getChildDevice(cdni)) { return }
          String cdriver = driverForType(crec.type as String)
          def cadded = addChildDevice(
            "neerpatel/hubspace",
            cdriver,
            cdni,
            [label: crec.name ?: cdni, isComponent: false]
          )
          cadded?.updateDataValue("hsType", crec.type as String)
          if (rec.id) { cadded?.updateDataValue("hsParentId", rec.id as String) }
          created << (crec.name ?: cdni)
          log.info "Installed child ${crec.name} (${crec.type}) for parent ${rec.name}"
          pauseExecution(150)
        }
      } else {
        // Standalone device; create directly
        String driver = driverForType(rec.type as String)
        def added = addChildDevice(
          "neerpatel/hubspace",
          driver,
          dni,
          [label: rec.name ?: dni, isComponent: false]
        )
        added?.updateDataValue("hsType", rec.type as String)
        created << (rec.name ?: dni)
        log.info "Installed ${rec.name} (${rec.type})"
      }
      created.each { state.addedDevices << [label: it, id: rec.id] }
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
  // Display only parent/standalone devices for clarity
  Set<String> childDnis = [] as Set
  devices.each { String k, Map v -> (v.children ?: []).each { String cid -> childDnis << "hubspace-${cid}" } }
  List lines = []
  devices.keySet().sort().each { String dni ->
    def rec = devices[dni]
    if (childDnis.contains(dni)) { return }
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
        // value pattern: fan-speed-<maxLevels>-<percent>
        String v = (value as String)
        Integer maxLevels = null
        Integer percent = null
        try {
          def m = (v =~ /fan-speed-(\d+)-(\d{1,3})/)
          if (m && m.size() > 0) {
            maxLevels = (m[0][1] as Integer)
            percent = (m[0][2] as Integer)
          }
        } catch (ignored) {}

        if (maxLevels != null) {
          try { cd.updateDataValue('fanMaxLevels', String.valueOf(maxLevels)) } catch (ignored) {}
        }

        // Map percent to Hubitat FanControl speed names
        String speedName = null
        if (maxLevels == null) {
          speedName = cd.currentValue('speed') ?: 'off'
        } else if (maxLevels >= 6) {
          if      (percent <= 16) speedName = 'low'
          else if (percent <= 33) speedName = 'medium-low'
          else if (percent <= 50) speedName = 'medium'
          else if (percent <= 66) speedName = 'medium-high'
          else if (percent <= 83) speedName = 'high'
          else                    speedName = 'on' // treat max as 'on'
        } else { // assume 3 levels
          if      (percent <= 33) speedName = 'low'
          else if (percent <= 66) speedName = 'medium'
          else                    speedName = 'high'
        }
        if (speedName) { cd.sendEvent(name: "speed", value: speedName) }
      }
      break
    case "fan-direction":
      cd.sendEvent(name: "direction", value: value as String)
      break
    case "fan-reverse":
      // Some fans report/use 'fan-reverse' instead of 'fan-direction'
      cd.sendEvent(name: "direction", value: (value as String))
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
