/*
 *  Hubspace Device Manager
 *  Provides connection to Hubspace cloud API and controls Hubspace devices.
 *  This driver is inspired by https://github.com/jdeath/Hubspace-Homeassistant
 *  and structured similar to drivers in https://github.com/DaveGut/HubitatActive.
 */


defdefinition(
  name: "HubSpace Bridge",
  namespace: "neerpatel/hubspace",
  author: "Neer Patel",
  importUrl: "https://raw.githubusercontent.com/neerpatel/hubspace/main/Hubitat/app/Hubspace/HubspaceDeviceManager.groovy", 
  description: "Discover and control HubSpace devices via cloud API",
  iconUrl: "",
  iconX2Url: "",
  installOnOpen: true,
  singleInstance: true,
  oauth: [
    name: "Hubspace",
    url: "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth",
    clientId: "hubspace_android",
    clientSecret: "",
    scope: "openid offline_access",
    responseType: "code",
    grantType: "authorization_code",
    accessTokenUrl: "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token",
    refreshTokenUrl: "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token",
    redirectUrl: "https://cloud.hubitat.com/oauth/st-callback"
  ]
)


preferences {
  page(name: "mainPage")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "HubSpace Bridge", install: true, uninstall: true) {
    section() {
      paragraph "To begin, you must authorize your Hubspace account with Hubitat."
      href(
        name: "oauth",
        title: "Connect to Hubspace",
        description: "Click here to log in to your Hubspace account and authorize Hubitat.",
        required: true,
        page: "oauthInitUrl"
      )
    }
    section("Polling") {
      input "pollSeconds", "number", title: "Poll interval (sec)", defaultValue: 30, required: true
    }
    section("Actions") {
      input name: "discoverNow", type: "button", title: "Discover Devices Now"
    }
  }
}


// Handle app page buttons
void appButtonHandler(String btn) {
  if (btn == "discoverNow") {
    log.debug "HubSpace Bridge: manual discovery requested"
    refreshIndexAndDiscover()
    // Log discovery status if available
    try {
      httpGet([uri: "${bridgeUrl}/discovery/status", timeout: 10]) { resp ->
        def d = resp?.data
        log.debug "Discovery status: known=${d?.known_count}, current=${d?.current_count}, missing=${d?.missing_known}"
      }
    } catch (Throwable t) {
      log.debug "Discovery status not available: ${t?.message}"
    }
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  loginToBridge()
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
}

private loginToBridge() {
  def body = [
    username: hsUser,
    password: hsPass,
    polling_interval: (pollSeconds ?: 30) as Integer
  ]
  httpPostJson([
    uri: "${bridgeUrl}/login",
    body: body,
    timeout: 10
  ]) { resp ->
    log.debug "Bridge login: ${resp.data}"
  }
}

private discoverDevices() {
  refreshIndexAndDiscover()
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

void refreshIndexAndDiscover() {
  // Ensure known set exists
  Set known = (state.knownIds ?: []) as Set

  // First try the bridge's discover endpoint (returns only newly added devices)
  Map discoverResp = null
  try {
    httpPostJson([
      uri: "${bridgeUrl}/discover",
      body: [:],
      timeout: 15
    ]) { resp ->
      discoverResp = resp?.data as Map
    }
  } catch (Throwable t) {
    log.warn "Discover endpoint failed (${t?.message}). Falling back to /devices diff."
  }

  if (discoverResp && discoverResp.new instanceof List) {
    List newList = (discoverResp.new as List)
    Integer addedCount = (discoverResp.added_count ?: newList.size()) as Integer
    Integer allCount = (discoverResp.all_count ?: -1) as Integer
    log.debug "Bridge discover: added=${addedCount}, total=${allCount}"

    // Create newly discovered devices
    newList.each { Map d ->
      String id   = (d.id ?: d.deviceId ?: d.uuid)?.toString()
      String name = (d.name ?: d.label ?: "HubSpace ${id}")?.toString()
      String type = (d.type ?: d.category ?: "device")?.toString().toLowerCase()
      if (!id) return
      if (!known.contains(id)) {
        String dni = "hubspace-${id}"
        String driver = driverForType(type)
        def child = getChildDevice(dni) ?: addChildDevice(
          "neerpatel/hubspace",
          driver,
          dni,
          [label: name, isComponent: false]
        )
        child?.updateDataValue("hsType", type as String)
        known << id
        log.debug "Discovered (bridge/new): ${name} (${type}) id=${id}"
      }
    }

    // Update local cache
    state.knownIds = known as List
    return
  }

  // Fallback: full /devices index + diff
  Map<String, Map> index = [:]
  httpGet([uri: "${bridgeUrl}/devices", timeout: 15]) { resp ->
    (resp.data ?: []).each { d ->
      String id   = (d.id ?: d.deviceId ?: d.uuid)?.toString()
      String name = (d.name ?: d.label ?: "HubSpace ${id}")?.toString()
      String type = (d.type ?: d.category ?: "device")?.toString().toLowerCase()
      if (!id) return
      index[id] = [id:id, name:name, type:type]
    }
  }

  // Create any new children
  index.each { String id, Map meta ->
    if (!known.contains(id)) {
      String dni = "hubspace-${id}"
      String driver = driverForType(meta.type)
      def child = getChildDevice(dni) ?: addChildDevice(
        "neerpatel/hubspace",
        driver,
        dni,
        [label: meta.name, isComponent: false]
      )
      child?.updateDataValue("hsType", meta.type as String)
      known << id
      log.debug "Discovered (fallback): ${meta.name} (${meta.type}) id=${id}"
    }
  }

  // Optionally: update labels/types for existing devices
  getChildDevices()?.each { cd ->
    String id = cd.deviceNetworkId - "hubspace-"
    Map meta = index[id]
    if (meta) {
      if (cd.label != meta.name) cd.setLabel(meta.name)
      cd.updateDataValue("hsType", meta.type as String)
    }
  }

  state.knownIds = known as List
}