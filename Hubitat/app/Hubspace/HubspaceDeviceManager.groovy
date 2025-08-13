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
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  log.debug "Initializing HubspaceDeviceManager"
  if (!state.accessToken) {
    log.info "Access token not found. Please authorize Hubitat with your Hubspace account."
    return
  }
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
}

def oauthInitUrl() {
  log.debug "oauthInitUrl()"
  state.redirectUri = "https://cloud.hubitat.com/oauth/st-callback"
  def authUrl = "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/auth?" +
                "response_type=code&" +
                "client_id=hubspace_android&" +
                "redirect_uri=${state.redirectUri}&" +
                "scope=openid%20offline_access"
  log.debug "Auth URL: ${authUrl}"
  return authUrl
}

def oauthCallback(params) {
  log.debug "oauthCallback(${params})"
  def code = params.code
  def accessTokenUrl = "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token"
  def body = [
    grant_type: "authorization_code",
    client_id: "hubspace_android",
    redirect_uri: state.redirectUri,
    code: code
  ]

  try {
    httpPostJson(
      uri: accessTokenUrl,
      body: body,
      timeout: 20
    ) { resp ->
      log.debug "OAuth Callback Response: ${resp.data}"
      state.accessToken = resp.data.access_token
      state.refreshToken = resp.data.refresh_token
      state.tokenExpires = now() + (resp.data.expires_in * 1000)
      log.info "Successfully obtained access token."
      discoverDevices()
    }
  } catch (e) {
    log.error "Error during OAuth callback: ${e.message}"
  }
}

def oauthRenew() {
  log.debug "oauthRenew()"
  def refreshTokenUrl = "https://accounts.hubspaceconnect.com/auth/realms/thd/protocol/openid-connect/token"
  def body = [
    grant_type: "refresh_token",
    client_id: "hubspace_android",
    refresh_token: state.refreshToken
  ]

  try {
    httpPostJson(
      uri: refreshTokenUrl,
      body: body,
      timeout: 20
    ) { resp ->
      log.debug "OAuth Renew Response: ${resp.data}"
      state.accessToken = resp.data.access_token
      state.refreshToken = resp.data.refresh_token
      state.tokenExpires = now() + (resp.data.expires_in * 1000)
      log.info "Successfully renewed access token."
    }
  } catch (e) {
    log.error "Error during OAuth renew: ${e.message}"
    state.accessToken = null // Invalidate token to force re-auth
  }
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
  httpGet([uri: "https://api2.afero.net/v1/accounts/${state.accountId}/metadevices/${devId}/state", headers: ["Authorization": "Bearer ${state.accessToken}"], timeout: 10]) { resp ->
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
    case "exhaust-fan": return "HubSpace Exhaust Fan (LAN)"
    case "lock": return "HubSpace Lock (LAN)"
    case "thermostat": return "HubSpace Thermostat (LAN)"
    case "valve": return "HubSpace Valve (LAN)"
    default: return "HubSpace Device (LAN)"
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
  httpPutJson([uri: "https://api2.afero.net/v1/accounts/${state.accountId}/metadevices/${devId}/state", headers: ["Authorization": "Bearer ${state.accessToken}"], body: payload, timeout: 10]) { resp ->
    if(resp.status != 200) log.warn "Command failed: $cmd $args -> ${resp.data}"
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
  // Normalize into Hubitat attributes
  def states = deviceData.states
  if (!states) return

  states.each { state ->
    def functionClass = state.functionClass
    def functionInstance = state.functionInstance
    def value = state.value

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
      case "fan-speed":
        cd.sendEvent(name: "speed", value: (value as String))
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
        }
        break
      case "mode":
        cd.sendEvent(name: "thermostatMode", value: value as String)
        break
      case "fan-speed": // for portable AC, this is a select
        cd.sendEvent(name: "thermostatFanMode", value: value as String)
        break
      case "sleep": // for portable AC, this is a select
        cd.sendEvent(name: "sleepMode", value: value as String)
        break
      // Add more cases for other device types and attributes as needed
      default:
        log.debug "Unhandled state: ${functionClass} - ${value}"
        break
    }
  }
}

