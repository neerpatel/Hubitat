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
  dynamicPage(name: "mainPage", title: "HubSpace Device Manager", install: true, uninstall: true) {
    section("HubSpace Credentials") {
      if (!state.accessToken) {
        paragraph "Enter your HubSpace username and password to authenticate."
        input "username", "text", title: "HubSpace Username/Email", required: true
        input "password", "password", title: "HubSpace Password", required: true
        input name: "authenticate", type: "button", title: "Connect to HubSpace"
      } else {
        paragraph "✅ Connected to HubSpace successfully!"
        paragraph "Token expires: ${state.tokenExpires ? new Date(state.tokenExpires) : 'Unknown'}"
        if (state.accountId) {
          paragraph "Account ID: ${state.accountId}"
        }
        input name: "refreshToken", type: "button", title: "Refresh Token"
        input name: "disconnectNow", type: "button", title: "Disconnect from HubSpace"
      }
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
  } else if (btn == "authenticate") {
    log.debug "HubSpace Bridge: authentication requested"
    if (settings.username && settings.password) {
      performWebAppLogin()
    } else {
      log.warn "Username and password are required"
    }
  } else if (btn == "refreshToken") {
    log.debug "HubSpace Bridge: token refresh requested"
    if (state.refreshToken) {
      refreshAccessToken()
    } else {
      log.warn "No refresh token available. Please re-authenticate."
    }
  }
}

def installed() { initialize() }
def updated()  { unschedule(); initialize() }

def initialize() {
  log.debug "Initializing HubspaceDeviceManager"
  if (!state.accessToken) {
    if (settings.username && settings.password) {
      log.info "Access token not found. Attempting initial HubSpace connection"
      performWebAppLogin()
    } else {
      log.info "Access token not found. Please authenticate with your HubSpace credentials."
      return
    }
  }
  getAccountId()
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
}

private performWebAppLogin() {
  log.debug "Starting HubSpace authentication flow"
  try {
    // Generate PKCE challenge
    def challenge = generateChallengeData()
    
    // Step 1: Get the authorization page to extract session info
    def authUrl = generateAuthUrl("/protocol/openid-connect/auth")
    def codeParams = [
      "response_type": "code",
      "client_id": "hubspace_android",
      "redirect_uri": "hubspace-app://loginredirect",
      "scope": "openid offline_access",
      "code_challenge": challenge.challenge,
      "code_challenge_method": "S256"
    ]
    
    log.debug "Getting auth page: ${authUrl} with params: ${codeParams}"
    
    httpGet([
      uri: authUrl,
      query: codeParams,
      followRedirects: false,
      textParser: true,
      contentType: "text/html",
      timeout: 20
    ]) { resp ->
      if (resp.status != 200) {
        throw new Exception("Failed to get auth page: ${resp.status}")
      }
      def html = resp.data.text
 
        // Try to find the login form action URL
      def formAction = extractFromHtml(html, '<form[^>]*id=["\']kc-form-login["\'][^>]*action=["\']([^"\']*)["\']') ?:
                      extractFromHtml(html, '<form[^>]*action=["\']([^"\']*)["\'][^>]*id=["\']kc-form-login["\']') ?:
                      extractFromHtml(html, 'action=["\']([^"\']*login-actions/authenticate[^"\']*)["\']')
      
      
      log.debug "Found form action: ${formAction}"  // formAction = 'https://accounts.hubspaceconnect.com/auth/realms/thd/login-actions/authenticate?session_code=ECB6CA8K8lpjo2bwKlGzSiR5FnBAKfg5LYRCaYWMGWo&execution=fc7165a5-4574-41ca-83e4-0b3c1a80e583&client_id=hubspace_android&tab_id=w6-UlDdV1eI'

      // Extract session_code, execution, tab_id from formAction URL if present
      def sessionCode = formAction =~ /session_code=([^&]*)/ ? (formAction =~ /session_code=([^&]*)/)[0][1] : null
      def execution = formAction =~ /execution=([^&]*)/ ? (formAction =~ /execution=([^&]*)/)[0][1] : null
      def tabId = formAction =~ /tab_id=([^&]*)/ ? (formAction =~ /tab_id=([^&]*)/)[0][1] : null

      // Fallback to extracting from HTML if not found in formAction
      if (!sessionCode) sessionCode = extractFromHtml(html, 'name="session_code" value="([^"]*)"')
      if (!execution) execution = extractFromHtml(html, 'name="execution" value="([^"]*)"')
      if (!tabId) tabId = extractFromHtml(html, 'name="tab_id" value="([^"]*)"')
      log.debug "Extracted auth page parameters: sessionCode=${sessionCode}, execution=${execution}, tabId=${tabId}"
      if (!sessionCode || !execution || !tabId) {
        throw new Exception("Failed to extract session parameters from auth page")
      }
      
      //log.debug "Extracted session parameters: sessionCode=${sessionCode}, execution=${execution}, tabId=${tabId}"
      
      // Step 2: Submit login credentials
      submitLoginCredentials(sessionCode, execution, tabId, challenge)
    }
  } catch (Exception e) {
    log.error "Authentication failed: ${e.message}"
    state.accessToken = null
  }
}

private generateChallengeData() {
  log.debug "Generating PKCE challenge data"
  
  // Generate code verifier - 40 random bytes, base64url encoded
  def random = new Random()
  def bytes = new byte[40]
  random.nextBytes(bytes)
  
  def codeVerifier = bytes.encodeBase64().toString()
    .replace('+', '-')
    .replace('/', '_')
    .replaceAll('[^a-zA-Z0-9\\-_]', '')
  
  // Generate code challenge - SHA256 hash of verifier, base64url encoded
  def digest = java.security.MessageDigest.getInstance("SHA-256")
  def hash = digest.digest(codeVerifier.getBytes("UTF-8"))
  
  def codeChallenge = hash.encodeBase64().toString()
    .replace('+', '-')
    .replace('/', '_')
    .replace('=', '')
  
  log.debug "Generated PKCE challenge: verifier length=${codeVerifier.length()}, challenge length=${codeChallenge.length()}"
  
  return [
    challenge: codeChallenge,
    verifier: codeVerifier
  ]
}

private submitLoginCredentials(String sessionCode, String execution, String tabId, Map challenge) {
  def loginUrl = generateAuthUrl("/login-actions/authenticate")
  def loginParams = [

    "session_code": sessionCode,
    "execution": execution,
    "client_id": "hubspace_android",
    "tab_id": tabId
  ]
  def loginData = [
    "username": settings.username,
    "password": settings.password,
    "credentialId": ""
  ]
  
  def headers = [
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "Dart/2.15 (dart:io)"
  ]
  
  log.debug "Submitting login credentials to: ${loginUrl}"
  log.debug "Login parameters: ${loginParams}"
  log.debug "Login data: ${loginData}"

  httpPost([
    uri: loginUrl,
    query: loginParams,
    followRedirects: false,
    body: loginData,
    headers: headers,
    textParser: true,
    timeout: 20
  ]) { resp ->
    log.debug "Login response: ${resp.status} ${resp.data}"
    if (resp.status == 302 || resp.status == 200) {
      // Check for redirect with authorization code
      def location = resp.headers["Location"]?.value
      if (location && location.contains("code=")) {
        def code = extractAuthCode(location)
        if (code) {
          log.debug "Successfully extracted authorization code"
          exchangeCodeForToken(code, challenge)
        } else {
          throw new Exception("Failed to extract authorization code from redirect")
        }
      } else {
        throw new Exception("No authorization code in response")
      }
    } else {
      throw new Exception("Login failed with status: ${resp.status}")
    }
  }
}

private exchangeCodeForToken(String code, Map challenge) {
  def tokenUrl = generateAuthUrl("/protocol/openid-connect/token")
  def tokenData = [
    "grant_type": "authorization_code",
    "client_id": "hubspace_android",
    "redirect_uri": "hubspace-app://loginredirect",
    "code": code,
    "code_verifier": challenge.verifier
  ]
  
  def headers = [
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "Dart/2.15 (dart:io)"
  ]
  
  log.debug "Exchanging code for token at: ${tokenUrl}"

  httpPost([
    uri: tokenUrl,
    body: tokenData,
    headers: headers,
    timeout: 20
  ]) { resp ->
    if (resp.status == 200) {
      def tokenResponse = resp.data
      state.accessToken = tokenResponse.access_token
      state.refreshToken = tokenResponse.refresh_token
      state.tokenExpires = now() + (tokenResponse.expires_in * 1000)
      
      log.info "Successfully obtained access token, expires in ${tokenResponse.expires_in} seconds"
      
      // Test the token and discover devices
      getAccountId()
      discoverDevices()
    } else {
      throw new Exception("Token exchange failed with status: ${resp.status}")
    }
  }
}

private refreshAccessToken() {
  if (!state.refreshToken) {
    log.warn "No refresh token available"
    return false
  }
  
  def tokenUrl = generateAuthUrl("/protocol/openid-connect/token")
  def tokenData = [
    "grant_type": "refresh_token",
    "client_id": "hubspace_android",
    "refresh_token": state.refreshToken
  ]
  
  def headers = [
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "Dart/2.15 (dart:io)"
  ]
  
  log.debug "Refreshing access token"
  
  try {
    httpPost([
      uri: tokenUrl,
      body: tokenData,
      headers: headers,
      timeout: 20
    ]) { resp ->
      if (resp.status == 200) {
        def tokenResponse = resp.data
        state.accessToken = tokenResponse.access_token
        if (tokenResponse.refresh_token) {
          state.refreshToken = tokenResponse.refresh_token
        }
        state.tokenExpires = now() + (tokenResponse.expires_in * 1000)
        
        log.info "Successfully refreshed access token"
        return true
      } else {
        throw new Exception("Token refresh failed with status: ${resp.status}")
      }
    }
  } catch (Exception e) {
    log.error "Error refreshing token: ${e.message}"
    state.accessToken = null
    state.refreshToken = null
    return false
  }
}

private generateAuthUrl(String endpoint) {
  endpoint = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint
  return "https://accounts.hubspaceconnect.com/auth/realms/thd/${endpoint}"
}

private extractFromHtml(String html, String pattern) {
  def matcher = html =~ pattern
  return matcher ? matcher[0][1] : null
}

private extractAuthCode(String url) {
  def matcher = url =~ /code=([^&]*)/
  return matcher ? java.net.URLDecoder.decode(matcher[0][1], "UTF-8") : null
}

private discoverDevices() {
  refreshIndexAndDiscover()
}


def pollAll() {
  if (!checkAndRenewToken()) {
    log.warn "Cannot poll devices - no valid access token"
    return
  }
  getChildDevices()?.each { c -> pollChild(c) }
}

def pollChild(cd) {
  def devId = cd.deviceNetworkId - "hubspace-"
  if (!checkAndRenewToken()) return
  
  try {
    def url = generateApiUrl("/v1/accounts/${state.accountId}/metadevices/${devId}/state")
    httpGet([
      uri: url, 
      headers: [
        "Authorization": "Bearer ${state.accessToken}",
        "Host": "api2.afero.net"
      ], 
      timeout: 10
    ]) { resp ->
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
  if (!checkAndRenewToken()) return
  
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
    def url = generateApiUrl("/v1/accounts/${state.accountId}/metadevices/${devId}/state")
    httpPutJson([
      uri: url, 
      headers: [
        "Authorization": "Bearer ${state.accessToken}",
        "Host": "api2.afero.net",
        "Content-Type": "application/json; charset=utf-8"
      ], 
      body: payload, 
      timeout: 10
    ]) { resp ->
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

private generateApiUrl(String endpoint) {
  endpoint = endpoint.startsWith("/") ? endpoint.substring(1) : endpoint
  return "https://api2.afero.net/${endpoint}"
}

private getAccountId() {
  if (!state.accountId) {
    if (!checkAndRenewToken()) return
    
    try {
      def url = generateApiUrl("/v1/users/me")
      httpGet([
        uri: url, 
        headers: [
          "Authorization": "Bearer ${state.accessToken}",
          "Host": "api2.afero.net"
        ], 
        timeout: 10
      ]) { resp ->
        if (resp.status != 200) {
          throw new Exception("Failed to retrieve account ID: ${resp.status}")
        }
        def jsonData = resp.data
        if (!jsonData?.accountAccess || jsonData.accountAccess.size() == 0) {
          throw new Exception("No account ID found")
        }
        state.accountId = jsonData.accountAccess[0].account.accountId
        log.debug "Retrieved account ID: ${state.accountId}"
      }
    } catch (Exception e) {
      log.error "Error retrieving account ID: ${e.message}"
      throw e
    }
  }
}

private checkAndRenewToken() {
  if (!state.accessToken) {
    log.warn "No access token available. Please authenticate with your HubSpace credentials."
    return false
  }
  
  // Check if token is expired and try to refresh
  if (state.tokenExpires && now() >= state.tokenExpires) {
    log.info "Access token expired, attempting to refresh"
    if (refreshAccessToken()) {
      log.info "Token refreshed successfully"
      return true
    } else {
      log.warn "Token refresh failed. Please re-authenticate."
      return false
    }
  }
  
  return true
}

void refreshIndexAndDiscover() {
  getAccountId()
  // Ensure known set exists
  Set known = (state.knownIds ?: []) as Set

  // Get all devices from Hubspace API
  List allDevices = []
  try {
    if (!checkAndRenewToken()) return
    
    def url = generateApiUrl("/v1/accounts/${state.accountId}/metadevices")
    httpGet([
      uri: url, 
      headers: [
        "Authorization": "Bearer ${state.accessToken}",
        "Host": "api2.afero.net"
      ], 
      params: ["expansions": "state"],
      timeout: 15
    ]) { resp ->
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

