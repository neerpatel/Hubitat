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
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "HubSpace Bridge", install: true, uninstall: true) {
    section() {
      if (!state.accessToken) {
        paragraph "To begin, you must authorize your Hubspace account with Hubitat."
        href(
          name: "oauth",
          title: "Connect to Hubspace",
          description: "Click here to log in to your Hubspace account and authorize Hubitat.",
          required: true,
          url: getAuthUrl()
        )
      } else {
        paragraph "✅ Connected to HubSpace successfully!"
        paragraph "Last token refresh: ${state.tokenExpires ? new Date(state.tokenExpires) : 'Never'}"
        input name: "disconnectNow", type: "button", title: "Disconnect from HubSpace"
      }
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
  } else if (btn == "disconnectNow") {
    log.debug "HubSpace Bridge: disconnect requested"
    state.accessToken = null
    state.refreshToken = null
    state.tokenExpires = null
    state.accountId = null
    log.info "Disconnected from HubSpace"
  } else if (btn == "saveToken") {
    log.debug "HubSpace Bridge: save token requested"
    if (settings.accessToken) {
      state.accessToken = settings.accessToken
      state.tokenExpires = now() + (24 * 60 * 60 * 1000) // Assume 24hr expiry
      log.info "Access token saved successfully"
      // Test the token by trying to get account info
      try {
        getAccountId()
        log.info "Token validated - account ID retrieved: ${state.accountId}"
        discoverDevices()
      } catch (Exception e) {
        log.error "Token validation failed: ${e.message}"
        state.accessToken = null
      }
    } else {
      log.warn "No access token provided"
    }
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  log.debug "Initializing HubspaceDeviceManager"
  if (!state.accessToken) {
    log.info "Access token not found. Please enter your HubSpace access token in the app settings."
    return
  }
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
}
private getAuthUrl() {
  log.debug "getAuthUrl()"
  state.redirectUri = "https://cloud.hubitat.com/oauth/st-callback"
  def authUrl = "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth?" +
                "response_type=code&" +
                "client_id=hubspace_android&" +
                "redirect_uri=${state.redirectUri}&" +
                "scope=openid%20offline_access"
  log.debug "Auth URL: ${authUrl}"
  return authUrl
}
private discoverDevices() {
  refreshIndexAndDiscover()
}


def pollAll() {
  checkAndRenewToken()
  getChildDevices()?.each { c -> pollChild(c) }
}

def pollChild(cd) {
  def devId = cd.deviceNetworkId - "hubspace-"
  checkAndRenewToken()
  try {
    httpGet([uri: "https://api2.afero.net/v1/accounts/${state.accountId}/metadevices/${devId}/state", headers: ["Authorization": "Bearer ${state.accessToken}"], timeout: 10]) { resp ->
      updateFromState(cd, resp.data)
    }
  } catch (Exception e) {
    log.warn "Error polling device ${cd.displayName}: ${e.message}"
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
  checkAndRenewToken()
  def payload = [
    metadeviceId: devId,
    values: [
      [
        functionClass: cmd,
        functionInstance: args.instance ?: null,
        value: args.value
      ]
    ]
  ]
  
  try {
    httpPutJson([uri: "https://api2.afero.net/v1/accounts/${state.accountId}/metadevices/${devId}/state", headers: ["Authorization": "Bearer ${state.accessToken}"], body: payload, timeout: 10]) { resp ->
      if(resp.status != 200) {
        log.warn "Command failed: $cmd $args -> ${resp.data}"
      } else {
        log.debug "Command successful: $cmd $args"
        // Poll the device to get updated state
        def childDevice = getChildDevices().find { it.deviceNetworkId == "hubspace-${devId}" }
        if (childDevice) {
          runIn(2, "pollChild", [data: childDevice])
        }
      }
    }
  } catch (Exception e) {
    log.error "Error sending command $cmd to device $devId: ${e.message}"
  }
}

private getAccountId() {
  if (!state.accountId) {
    checkAndRenewToken()
    httpGet([uri: "https://api2.afero.net/v1/users/me", headers: ["Authorization": "Bearer ${state.accessToken}"], timeout: 10]) { resp ->
      state.accountId = resp.data.accountAccess[0].account.accountId
      log.debug "Retrieved account ID: ${state.accountId}"
    }
  }
}

private checkAndRenewToken() {
  if (state.accessToken && state.tokenExpires && now() >= state.tokenExpires) {
    oauthRenew()
  }
}

void refreshIndexAndDiscover() {
  getAccountId()
  // Ensure known set exists
  Set known = (state.knownIds ?: []) as Set

  // Get all devices from Hubspace API
  List allDevices = []
  try {
    checkAndRenewToken()
    httpGet([uri: "https://api2.afero.net/v1/accounts/${state.accountId}/metadevices", headers: ["Authorization": "Bearer ${state.accessToken}"], timeout: 15]) { resp ->
      allDevices = resp?.data as List
    }
  } catch (Throwable t) {
    log.warn "Failed to retrieve devices from Hubspace API: ${t?.message}"
    return
  }

  // Process discovered devices
  allDevices.each { Map d ->
    String id   = (d.id)?.toString()
    String name = (d.friendly_name ?: d.default_name ?: "HubSpace ${id}")?.toString()
    String type = (d.device_class)?.toString().toLowerCase()
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
      log.debug "Discovered: ${name} (${type}) id=${id}"
    }
  }

  // Remove devices that are no longer in Hubspace
  def currentChildDevices = getChildDevices()
  currentChildDevices.each { cd ->
    String id = cd.deviceNetworkId - "hubspace-"
    if (!allDevices.find { it.id == id }) {
      log.debug "Removing device no longer in Hubspace: ${cd.displayName} (${id})"
      deleteChildDevice(cd.deviceNetworkId)
      known.remove(id)
    }
  }

  state.knownIds = known as List
}

private updateFromState(cd, Map deviceData) {
  // Handle both direct state values and states array format
  def states = deviceData.states ?: deviceData
  if (!states) return

  // If states is a list, process each state
  if (states instanceof List) {
    states.each { state ->
      processStateValue(cd, state.functionClass, state.functionInstance, state.value)
    }
  } else {
    // Handle direct state object format
    states.each { key, value ->
      processStateValue(cd, key, null, value)
    }
  }
}

private processStateValue(cd, String functionClass, String functionInstance, value) {
  switch (functionClass) {
    case "power":
      cd.sendEvent(name: "switch", value: value == "on" ? "on" : "off")
      break
    case "brightness":
      cd.sendEvent(name: "level", value: (value as int))
      break
    case "color-temperature":
      cd.sendEvent(name: "colorTemperature", value: (value as int))
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
    case "motion-action":
      cd.sendEvent(name: "motionAction", value: value as String)
      break
    case "sensitivity":
      cd.sendEvent(name: "sensitivity", value: value as String)
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

