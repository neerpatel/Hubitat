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
  
  discoverDevices()
  if (state.knownIds == null) state.knownIds = []
  schedule("*/${Math.max(15, pollSeconds)} * * * * ?", pollAll)
  // Health monitor
  schedule("*/${Math.max(30, (settings.healthSeconds ?: 120) as int)} * * * * ?", healthCheck)
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
    
    def cookieHeader = null
    httpGet([
      uri: authUrl,
      query: codeParams,
      followRedirects: false,
      textParser: true,
      contentType: "text/html",
      headers: [
        "User-Agent": "Dart/3.1 (dart:io)",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
      ],
      timeout: 20
    ]) { resp ->
      if (resp.status != 200) {
        throw new Exception("Failed to get auth page: ${resp.status}")
      }
      def html = resp.data.text

      // Capture cookies from the auth page to include in subsequent POST
      try {
        def cookies = []
        resp.headers?.each { k, v ->
          if (k?.toString()?.equalsIgnoreCase('Set-Cookie')) {
            // Only send cookie name=value
            def nv = v?.value?.toString()?.tokenize(';')?.getAt(0)
            if (nv) cookies << nv
          }
        }
        if (cookies) {
          cookieHeader = cookies.unique().join('; ')
          state._authCookies = cookieHeader
          log.debug "Captured ${cookies.size()} cookies from auth page"
        } else {
          state._authCookies = null
        }
      } catch (ignored) {
        // Ignore cookie parse errors; continue without cookies
      }
 
        // Try to find the login form action URL
      def formAction = extractFromHtml(html, '<form[^>]*id=["\']kc-form-login["\'][^>]*action=["\']([^"\']*)["\']') ?:
                      extractFromHtml(html, '<form[^>]*action=["\']([^"\']*)["\'][^>]*id=["\']kc-form-login["\']') ?:
                      extractFromHtml(html, 'action=["\']([^"\']*login-actions/authenticate[^"\']*)["\']')
      
      // Decode HTML entities (matching shell script behavior)
      if (formAction) {
        formAction = formAction.replace('&amp;', '&')
      }
      
      log.debug "Found form action: ${formAction}"

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
      
      // Step 2: Submit login credentials using the extracted form action URL
      submitLoginCredentials(formAction, challenge)
    }
  } catch (Exception e) {
    log.error "Authentication failed: ${e.message}"
    state.accessToken = null
  }
}

private generateChallengeData() {
  log.debug "Generating PKCE challenge data"
  
  // Generate code verifier - 40 random bytes, base64url encoded (matching shell script)
  def random = new Random()
  def bytes = new byte[40]
  random.nextBytes(bytes)
  
  // Base64 encode, then convert to base64url format and remove any non-alphanumeric chars
  def codeVerifier = bytes.encodeBase64().toString()
    .replace('+', '-')
    .replace('/', '_')
    .replace('=', '')
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

private submitLoginCredentials(String loginUrl, Map challenge) {
  // Use the form action URL directly (as extracted from the auth page)
  log.debug "Using form action URL: ${loginUrl}"
  
  // Body data only contains credentials (matching shell script format)
  def loginData = [
    "username": settings.username,
    "password": settings.password,
    "credentialId": ""
  ]
  
  def headers = [
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "Dart/3.1 (dart:io)",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
  ]
  // Include cookies from the auth GET if present
  if (state._authCookies) {
    headers["Cookie"] = state._authCookies
    // Optional Referer for stricter IdP enforcement
    headers["Referer"] = generateAuthUrl("/protocol/openid-connect/auth")
  }
  
  log.debug "Submitting login credentials to: ${loginUrl}"
  // Do NOT log raw credentials
  log.debug "Login data: [username:${settings.username}, password:***, credentialId:<hidden>]"

  httpPost([
    uri: loginUrl,
    followRedirects: false,
    body: loginData,
    headers: headers,
    contentType: 'application/x-www-form-urlencoded',
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
    "User-Agent": "Dart/3.1 (dart:io)",
    "Accept": "application/json",
    "Host": "accounts.hubspaceconnect.com"
  ]
  
  log.debug "Exchanging code for token at: ${tokenUrl}"

  httpPost([
    uri: tokenUrl,
    body: tokenData,
    headers: headers,
    contentType: 'application/x-www-form-urlencoded',
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
    "User-Agent": "Dart/3.1 (dart:io)",
    "Accept": "application/json",
    "Host": "accounts.hubspaceconnect.com"
  ]
  
  log.debug "Refreshing access token"
  
  try {
    httpPost([
      uri: tokenUrl,
      body: tokenData,
      headers: headers,
      contentType: 'application/x-www-form-urlencoded',
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

// Deprecated: direct cloud API disabled; bridge handles API URL generation
private generateApiUrl(String endpoint) { return "" }

// Deprecated: direct cloud API disabled; accountId comes from Node bridge login
private getAccountId() { }

// Deprecated: direct cloud API disabled; bridge manages auth
private checkAndRenewToken() { false }

void refreshIndexAndDiscover() {
  // Ensure known set exists
  Set known = (state.knownIds ?: []) as Set

  // Get all devices from Hubspace API
  List allDevices = []
  try {
    log.info "[NodeBridge] GET /devices"
    httpGet([
      uri: "${settings.nodeBridgeUrl}/devices",
      params: [session: state.nodeSessionId],
      timeout: 15
    ]) { resp ->
      log.debug "[NodeBridge] devices status=${resp.status} count=${resp?.data?.size()}"
      allDevices = resp?.data as List
    }
  } catch (Throwable t) {
    log.warn "Failed to retrieve devices from Hubspace API: ${t?.message}"
    return
  }

  // Process discovered devices
  allDevices.each { Map d ->
    // Shape can vary by endpoint/expansion.
    String typeId = (d.typeId ?: d.type)?.toString()
    if (typeId && typeId != 'metadevice.device') {
      // Skip rooms, groups, home containers, etc.
      return
    }
    String id = (d.id ?: d.deviceId ?: d.metadeviceId ?: d.device_id)?.toString()
    String type = (
      d.device_class ?: d?.description?.device?.deviceClass ?: d?.description?.deviceClass
    )?.toString()?.toLowerCase()
    String name = (
      d.friendlyName ?: d.friendly_name ?: d?.description?.device?.friendlyName ?: d.default_name ?: (id ? "HubSpace ${id}" : null)
    )?.toString()
    if (!id || !type) return

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
