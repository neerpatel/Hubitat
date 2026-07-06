/*
 * HubSpace Device Manager
 *
 * Direct Hubitat app integration for HubSpace cloud devices.
 * The app owns HubSpace auth, discovery, polling, and command routing.
 */

String appVersion() { '0.3.0' }

definition(
    name: 'HubSpace Device Manager',
    namespace: 'neerpatel/hubspace',
    author: 'Neer Patel',
    version: appVersion(),
    importUrl: 'https://raw.githubusercontent.com/neerpatel/Hubitat/refs/heads/main/Hubitat/Hubspace/app/HubspaceDeviceManager.groovy',
    description: 'Discover and control HubSpace devices via cloud API',
    iconUrl: '',
    iconX2Url: '',
    installOnOpen: true,
    singleInstance: true,
    singleThreaded: true
)

preferences {
    page(name: 'mainPage')
    page(name: 'addDevicesPage')
    page(name: 'addDevStatus')
    page(name: 'listDevices')
    page(name: 'removeDevicesPage')
    page(name: 'removeDevStatus')
    page(name: 'uninstalled')
}

private String hubspaceAuthBase() { 'https://accounts.hubspaceconnect.com/auth/realms/thd' }
private String hubspaceAuthHost() { 'accounts.hubspaceconnect.com' }
private String hubspaceApiBase() { 'https://api2.afero.net' }
private String hubspaceApiHost() { 'api2.afero.net' }
private String hubspaceDataHost() { 'semantics2.afero.net' }
private String hubspaceClientId() { 'hubspace_android' }
private String hubspaceRedirectUri() { 'hubspace-app://loginredirect' }
private String hubspaceRequestedWith() { 'io.afero.partner.hubspace' }
private String hubspaceUserAgent() { 'Mozilla/5.0 (Linux; Android 15; Hubitat Groovy Build/test; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.63 Mobile Safari/537.36' }
private Long hubspaceRefreshBufferMs() { 5000L }

private Map hubspaceDefaultHeaders(Map extra = [:]) {
    [
        'User-Agent': hubspaceUserAgent(),
        'accept-encoding': 'gzip'
    ] + (extra ?: [:])
}

private Map buildPkcePair() {
    String verifier = ''
    while (verifier.length() < 64) {
        verifier += java.util.UUID.randomUUID().toString().replace('-', '')
    }
    verifier = verifier.take(64)
    byte[] digest = java.security.MessageDigest.getInstance('SHA-256').digest(verifier.getBytes('UTF-8'))
    String challenge = base64UrlEncode(digest)
    [verifier: verifier, challenge: challenge]
}

private String base64UrlEncode(byte[] bytes) {
    bytes.encodeBase64().toString().replace('+', '-').replace('/', '_').replace('=', '')
}

private String urlEncode(value) {
    java.net.URLEncoder.encode(value?.toString() ?: '', 'UTF-8')
}

private String urlDecode(String value) {
    java.net.URLDecoder.decode(value ?: '', 'UTF-8')
}

private String toQueryString(Map params) {
    (params ?: [:]).collect { k, v -> "${urlEncode(k)}=${urlEncode(v)}" }.join('&')
}

private String authUrl(String path, Map query = [:]) {
    String clean = path?.startsWith('/') ? path.substring(1) : path
    String queryString = toQueryString(query)
    queryString ? "${hubspaceAuthBase()}/${clean}?${queryString}" : "${hubspaceAuthBase()}/${clean}"
}

private String apiUrl(String path) {
    String clean = path?.startsWith('/') ? path.substring(1) : path
    "${hubspaceApiBase()}/${clean}"
}

private Map collectHeaders(resp) {
    Map headers = [:].withDefault { [] }
    List rawSets = []
    try { if (resp?.headers != null) rawSets << resp.headers } catch (ignored) {}
    try { if (resp?.allHeaders != null) rawSets << resp.allHeaders } catch (ignored) {}
    try { if (resp?.getHeaders() != null) rawSets << resp.getHeaders() } catch (ignored) {}

    rawSets.each { raw ->
        if (raw instanceof Map) {
            raw.each { k, v -> addHeaderValues(headers, k?.toString(), v) }
        } else if (raw instanceof Collection) {
            raw.each { h ->
                addHeaderObject(headers, h)
            }
        } else if (raw instanceof Object[]) {
            raw.each { h -> addHeaderObject(headers, h) }
        } else {
            addHeaderObject(headers, raw)
        }
    }

    headers.collectEntries { String k, List v ->
        [(k.toLowerCase()): v.findAll { it != null }.collect { it.toString() }]
    }
}

private void addHeaderObject(Map headers, header) {
    String name = null
    def value = null
    try { name = header?.name?.toString() } catch (ignored) {}
    try { if (!name && header?.respondsTo('getName')) name = header.getName()?.toString() } catch (ignored) {}
    try { value = header?.value } catch (ignored) {}
    try { if (value == null && header?.respondsTo('getValue')) value = header.getValue() } catch (ignored) {}
    addHeaderValues(headers, name, value)
}

private void addHeaderValues(Map headers, String name, value) {
    if (!name || value == null) {
        return
    }
    if (value instanceof Collection) {
        value.each { headers[name] << it?.toString() }
    } else if (value instanceof Object[]) {
        value.each { headers[name] << it?.toString() }
    } else {
        headers[name] << value.toString()
    }
}

private String firstHeader(Map headers, String name) {
    List values = headers?."${name?.toLowerCase()}"
    values ? values[0]?.toString() : null
}

private String responseText(resp) {
    try {
        if (resp?.data instanceof String) {
            return resp.data
        }
    } catch (ignored) {}
    try {
        if (resp?.getData() instanceof String) {
            return resp.getData()
        }
    } catch (ignored) {}
    try {
        return resp?.data?.toString()
    } catch (ignored) {}
    null
}

private String extractAuthCode(String url) {
    def matcher = (url ?: '') =~ /[?&]code=([^&]+)/
    matcher ? urlDecode(matcher[0][1].toString()) : null
}

private String extractFromHtml(String html, String pattern) {
    def matcher = (html ?: '') =~ pattern
    matcher ? matcher[0][1]?.toString() : null
}

private String loginFormAction(String html) {
    String action = extractFromHtml(html, /(?is)<form\b(?=[^>]*\bid=["']kc-form-login["'])(?=[^>]*\baction=["']([^"']+)["'])[^>]*>/)
    action?.replace('&amp;', '&')
}

private String hiddenField(String html, String fieldName) {
    extractFromHtml(html, /(?is)<input\b(?=[^>]*\bname=["']${java.util.regex.Pattern.quote(fieldName)}["'])(?=[^>]*\bvalue=["']([^"']*)["'])[^>]*>/)
}

private Map parseCookies(Map headers) {
    List rawCookies = headers?.'set-cookie' ?: []
    List cookies = rawCookies.collect { String cookie ->
        cookie?.split(';', 2)?.first()
    }.findAll { it }
    [cookieHeader: cookies.join('; '), values: cookies]
}

private Map normalizeHttpResponse(resp) {
    [
        status : resp?.status,
        data   : resp?.data,
        text   : responseText(resp),
        headers: collectHeaders(resp)
    ]
}

private Map doHttp(String method, Map params) {
    Map result = [:]
    if (method == 'GET') {
        httpGet(params) { resp -> result = normalizeHttpResponse(resp) }
    } else if (method == 'POST') {
        httpPost(params) { resp -> result = normalizeHttpResponse(resp) }
    } else if (method == 'PUT') {
        httpPut(params) { resp -> result = normalizeHttpResponse(resp) }
    } else {
        throw new IllegalArgumentException("Unsupported HTTP method: ${method}")
    }
    result
}

private boolean hasHubspaceSession() {
    !!(state.hsAccessToken && state.hsRefreshToken && state.hsAccountId)
}

private String currentHubspaceBearer() {
    (state.hsIdToken ?: state.hsAccessToken)?.toString()
}

private void clearHubspaceAuthState(String reason = null) {
    state.hsAccessToken = null
    state.hsIdToken = null
    state.hsRefreshToken = null
    state.hsTokenExpiry = null
    state.hsAccountId = null
    state.hsAuthCookies = null
    state.hsLastAuthError = reason
}

private void clearLegacyBridgeState() {
    state.nodeSessionId = null
    state.nodeBridgeAccountId = null
    state.bridgeHealth = null
    app?.removeSetting('nodeBridgeUrl')
    app?.removeSetting('healthSeconds')
}

private void storeHubspaceTokens(Map tokenJson) {
    state.hsIdToken = tokenJson.id_token ?: state.hsIdToken
    state.hsAccessToken = tokenJson.access_token ?: state.hsAccessToken
    state.hsRefreshToken = tokenJson.refresh_token ?: state.hsRefreshToken
    Integer expiresIn = ((tokenJson.expires_in ?: 0) as Integer)
    if (expiresIn > 0) {
        state.hsTokenExpiry = now() + Math.max(0, expiresIn - 5) * 1000L
    }
}

private Map parseJsonText(String text) {
    if (!text) {
        return [:]
    }
    def parsed = new groovy.json.JsonSlurper().parseText(text)
    parsed instanceof Map ? parsed : [:]
}

private boolean isUnauthorized(Throwable t) {
    String message = t?.message ?: ''
    message.contains('401') || message.toLowerCase().contains('unauthorized')
}

private Map requireHubspaceSession(boolean forceLogin = false) {
    if (forceLogin || !hasHubspaceSession()) {
        return hubspaceLogin()
    }
    if (!state.hsAccountId) {
        return hubspaceLogin()
    }
    refreshTokenIfNeeded(false)
}

private Map hubspaceLogin() {
    if (!settings.username || !settings.password) {
        throw new IllegalStateException('HubSpace username and password are required')
    }

    Map pkce = buildPkcePair()
    Map authResp = doHttp('GET', [
        uri            : authUrl('protocol/openid-connect/auth', [
            response_type        : 'code',
            client_id            : hubspaceClientId(),
            redirect_uri         : hubspaceRedirectUri(),
            scope                : 'openid offline_access',
            code_challenge       : pkce.challenge,
            code_challenge_method: 'S256'
        ]),
        headers        : hubspaceDefaultHeaders([
            Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
        ]),
        contentType    : 'text/html',
        textParser     : true,
        followRedirects: false,
        timeout        : 20
    ])

    String loginHtml = authResp.text ?: ''
    Map loginCookies = parseCookies(authResp.headers)
    String action = loginFormAction(loginHtml)
    String sessionCode = action?.find(/session_code=[^&]+/) ? extractFromHtml(action, /session_code=([^&]+)/) : hiddenField(loginHtml, 'session_code')
    String execution = action?.find(/execution=[^&]+/) ? extractFromHtml(action, /execution=([^&]+)/) : hiddenField(loginHtml, 'execution')
    String tabId = action?.find(/tab_id=[^&]+/) ? extractFromHtml(action, /tab_id=([^&]+)/) : hiddenField(loginHtml, 'tab_id')

    if (!action || !sessionCode || !execution || !tabId) {
        throw new IllegalStateException('Unable to parse HubSpace login form')
    }

    Map postResp = doHttp('POST', [
        uri               : authUrl('login-actions/authenticate', [
            session_code: sessionCode,
            execution   : execution,
            client_id   : hubspaceClientId(),
            tab_id      : tabId
        ]),
        headers           : hubspaceDefaultHeaders([
            'Content-Type'   : 'application/x-www-form-urlencoded',
            'x-requested-with': hubspaceRequestedWith(),
            Accept           : 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            Cookie           : loginCookies.cookieHeader
        ]),
        body              : toQueryString([
            username    : settings.username,
            password    : settings.password,
            credentialId: ''
        ]),
        requestContentType: 'application/x-www-form-urlencoded',
        contentType       : 'text/html',
        textParser        : true,
        followRedirects   : false,
        timeout           : 20
    ])

    String otpHtml = postResp.text ?: ''
    if ((postResp.status as Integer) == 200 && otpHtml.contains('kc-otp-login-form')) {
        throw new IllegalStateException('HubSpace verification-code login is not supported by this app')
    }

    String location = firstHeader(postResp.headers, 'location')
    String authCode = extractAuthCode(location)
    if (!authCode) {
        throw new IllegalStateException("HubSpace login did not return an auth code (status ${postResp.status})")
    }

    Map tokenResp = doHttp('POST', [
        uri               : authUrl('protocol/openid-connect/token'),
        headers           : hubspaceDefaultHeaders([
            'Content-Type': 'application/x-www-form-urlencoded',
            Accept        : 'application/json',
            Host          : hubspaceAuthHost()
        ]),
        body              : toQueryString([
            grant_type   : 'authorization_code',
            client_id    : hubspaceClientId(),
            redirect_uri : hubspaceRedirectUri(),
            code         : authCode,
            code_verifier: pkce.verifier
        ]),
        requestContentType: 'application/x-www-form-urlencoded',
        contentType       : 'application/json',
        timeout           : 20
    ])

    Map tokenJson = tokenResp.data instanceof Map ? tokenResp.data : parseJsonText(tokenResp.text)
    if (!tokenJson?.access_token) {
        throw new IllegalStateException('HubSpace token exchange failed')
    }

    clearHubspaceAuthState()
    storeHubspaceTokens(tokenJson)
    state.hsAuthCookies = loginCookies.values

    Map me = hubspaceGetMe(false)
    String accountId = me?.accountAccess?.getAt(0)?.account?.accountId?.toString()
    if (!accountId) {
        clearHubspaceAuthState('Unable to resolve HubSpace account ID')
        throw new IllegalStateException('Unable to resolve HubSpace account ID')
    }

    state.hsAccountId = accountId
    state.hsLastAuthError = null
    log.info "HubSpace login successful (app v${appVersion()}) accountId=${state.hsAccountId}"
    [accountId: state.hsAccountId]
}

private Map refreshTokenIfNeeded(boolean forceRefresh = false) {
    if (!state.hsRefreshToken) {
        throw new IllegalStateException('HubSpace refresh token is missing')
    }
    Long expiryMs = (state.hsTokenExpiry ?: 0L) as Long
    if (!forceRefresh && expiryMs > 0L && now() < (expiryMs - hubspaceRefreshBufferMs())) {
        return [access_token: state.hsAccessToken, refresh_token: state.hsRefreshToken]
    }

    Map refreshResp = doHttp('POST', [
        uri               : authUrl('protocol/openid-connect/token'),
        headers           : hubspaceDefaultHeaders([
            'Content-Type': 'application/x-www-form-urlencoded',
            Accept        : 'application/json',
            Host          : hubspaceAuthHost()
        ]),
        body              : toQueryString([
            grant_type   : 'refresh_token',
            client_id    : hubspaceClientId(),
            refresh_token: state.hsRefreshToken,
            scope        : 'openid email offline_access profile'
        ]),
        requestContentType: 'application/x-www-form-urlencoded',
        contentType       : 'application/json',
        timeout           : 20
    ])

    Map refreshJson = refreshResp.data instanceof Map ? refreshResp.data : parseJsonText(refreshResp.text)
    if (!refreshJson?.access_token) {
        clearHubspaceAuthState('HubSpace token refresh failed')
        throw new IllegalStateException('HubSpace token refresh failed')
    }

    storeHubspaceTokens(refreshJson)
    state.hsLastAuthError = null
    log.debug "HubSpace token refreshed (app v${appVersion()})"
    refreshJson
}

private Map hubspaceAuthorizedRequest(String method, String url, Map request = [:], boolean retry = true) {
    requireHubspaceSession(false)
    Map params = [
        uri        : url,
        headers    : hubspaceDefaultHeaders([
            Authorization: "Bearer ${currentHubspaceBearer()}",
            Host         : request.hostHeader ?: hubspaceDataHost()
        ]) + (request.headers ?: [:]),
        contentType: request.contentType ?: 'application/json',
        timeout    : request.timeout ?: 20
    ]

    if (request.requestContentType) {
        params.requestContentType = request.requestContentType
    }
    if (request.body != null) {
        params.body = request.body
    }
    if (request.textParser != null) {
        params.textParser = request.textParser
    }
    if (request.followRedirects != null) {
        params.followRedirects = request.followRedirects
    }

    try {
        return doHttp(method, params)
    } catch (Throwable t) {
        if (retry && isUnauthorized(t)) {
            log.warn "HubSpace request unauthorized, refreshing token and retrying (app v${appVersion()})"
            refreshTokenIfNeeded(true)
            return hubspaceAuthorizedRequest(method, url, request, false)
        }
        throw t
    }
}

private Map hubspaceGetMe(boolean ensureSession = true) {
    if (ensureSession) {
        requireHubspaceSession(false)
        Map resp = hubspaceAuthorizedRequest('GET', apiUrl('/v1/users/me'), [hostHeader: hubspaceApiHost()])
        return resp.data instanceof Map ? resp.data : parseJsonText(resp.text)
    }
    Map resp = doHttp('GET', [
        uri        : apiUrl('/v1/users/me'),
        headers    : hubspaceDefaultHeaders([
            Authorization: "Bearer ${currentHubspaceBearer()}",
            Host         : hubspaceApiHost()
        ]),
        contentType: 'application/json',
        timeout    : 20
    ])
    resp.data instanceof Map ? resp.data : parseJsonText(resp.text)
}

private List hubspaceGetDevices() {
    String url = apiUrl("/v1/accounts/${state.hsAccountId}/metadevices?expansions=state,capabilities,semantics")
    Map resp = hubspaceAuthorizedRequest('GET', url)
    def data = resp.data
    if (!(data instanceof List)) {
        return []
    }
    data.collect { Map d ->
        [
            id         : d.id ?: d.deviceId ?: d.metadeviceId ?: d.device_id,
            deviceId   : d.deviceId ?: d.device_id,
            typeId     : d.typeId ?: d.type,
            device_class: d.device_class ?: d?.description?.device?.deviceClass ?: d?.description?.deviceClass,
            friendlyName: d.friendlyName ?: d.friendly_name ?: d?.description?.device?.friendlyName ?: d.default_name,
            children   : d.children ?: [],
            semantics  : d.semantics,
            capabilities: d.capabilities,
            description: d.description,
            states     : d.state ?: d.states
        ]
    }
}

private Map hubspaceGetState(String deviceId) {
    String url = apiUrl("/v1/accounts/${state.hsAccountId}/metadevices/${deviceId}/state")
    Map resp = hubspaceAuthorizedRequest('GET', url)
    resp.data instanceof Map ? resp.data : parseJsonText(resp.text)
}

private Map hubspacePutState(String deviceId, List values) {
    String url = apiUrl("/v1/accounts/${state.hsAccountId}/metadevices/${deviceId}/state")
    Map resp = hubspaceAuthorizedRequest('PUT', url, [
        body              : [metadeviceId: "${deviceId}", values: values],
        requestContentType: 'application/json',
        contentType       : 'application/json'
    ])
    resp.data instanceof Map ? resp.data : (resp.text ? parseJsonText(resp.text) : [ok: true])
}

private String authStatusLine() {
    if (hasHubspaceSession()) {
        Long expiry = (state.hsTokenExpiry ?: 0L) as Long
        String expires = expiry ? new Date(expiry).toString() : 'unknown'
        return "Connected to HubSpace\nAccount ID: ${state.hsAccountId}\nToken expires: ${expires}"
    }
    String error = state.hsLastAuthError ? "\nLast error: ${state.hsLastAuthError}" : ''
    "Not connected to HubSpace${error}"
}

private String resolveDeviceClass(Map d) {
    (
        d.device_class ?:
        d?.description?.device?.deviceClass ?:
        d?.description?.deviceClass ?:
        d?.semantics?.device?.deviceClass ?:
        d?.semantics?.deviceClass ?:
        d?.capabilities?.device?.deviceClass ?:
        d?.capabilities?.deviceClass
    )?.toString()?.toLowerCase()
}

private String resolveDeviceName(Map d, String id) {
    (
        d.friendlyName ?:
        d.friendly_name ?:
        d?.description?.device?.friendlyName ?:
        d?.description?.deviceName ?:
        d?.semantics?.device?.friendlyName ?:
        d?.semantics?.deviceName ?:
        d.default_name ?:
        (id ? "HubSpace ${id}" : null)
    )?.toString()
}

private childFromTarget(target) {
    if (target == null) {
        return null
    }
    try {
        if (target?.deviceNetworkId) {
            return target
        }
    } catch (ignored) {}
    if (target instanceof Map && target.dni) {
        return getChildDevice(target.dni.toString())
    }
    if (target instanceof String) {
        return getChildDevice(target.toString())
    }
    null
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: 'HubSpace Device Manager', install: true, uninstall: true) {
        section('HubSpace Credentials') {
            input 'username', 'text', title: 'HubSpace Username/Email', required: true
            input 'password', 'password', title: 'HubSpace Password', required: true
            paragraph authStatusLine()
            input name: 'connectHubspace', type: 'button', title: hasHubspaceSession() ? 'Reconnect to HubSpace' : 'Connect to HubSpace'
        }
        section('Polling') {
            input 'pollSeconds', 'number', title: 'Poll interval (sec)', defaultValue: 30, required: true
        }
        section('Actions') {
            input name: 'discoverNow', type: 'button', title: 'Discover Devices Now'
            if (getChildDevices()?.size() > 0) {
                paragraph "Discovered devices: ${getChildDevices().size()}"
            }
            href 'removeDevicesPage', title: 'Remove Installed Devices', description: 'Select and remove HubSpace child devices'
        }
        section('Device Discovery & Add') {
            paragraph 'Use HubSpace cloud discovery, then select which devices to add.'
            href 'addDevicesPage', title: 'Discover and Add Devices', description: 'Scan HubSpace and choose devices to install'
            href 'listDevices', title: 'List Discovered Devices', description: 'Show discovered devices and install state'
        }
        section('Uninstall') {
            paragraph 'Uninstall all the devices'
            href 'uninstalled', title: 'Uninstall all Devices', description: 'Select and remove HubSpace child devices'
        }
    }
}

def uninstalled() {
    List lines = []
    log.info 'Uninstalling all HubSpace devices'
    def childDevice = getAllChildDevices()
    log.info "Found child device: ${childDevice.size()}"
    getAllChildDevices().each {
        log.info "Attempting to remove child device ${it.deviceNetworkId}"
        lines << "<p style='font-size:14px'>${it.deviceNetworkId}</p>"
        deleteChildDevice(it.deviceNetworkId)
        lines << "<p style='font-size:14px'>${it.deviceNetworkId}</p>"
        log.info "Removed child device ${it.deviceNetworkId}"
    }
    lines << "<p style='font-size:14px'>All child devices removed.</p>"
    dynamicPage(name: 'uninstalled', title: 'Uninstalling Devices', install: false) {
        section('Removing all child devices...') {
            paragraph "<p style='font-size:14px'>${lines.join('\n')}</p>"
        }
    }
}

void appButtonHandler(String btn) {
    if (btn == 'discoverNow') {
        log.debug "HubSpace manual discovery requested (app v${appVersion()})"
        refreshIndexAndDiscover()
    } else if (btn == 'connectHubspace') {
        log.debug "HubSpace connect requested (app v${appVersion()})"
        try {
            clearHubspaceAuthState()
            hubspaceLogin()
        } catch (Throwable t) {
            clearHubspaceAuthState(t?.message)
            log.error "HubSpace login failed (app v${appVersion()}): ${t?.message}"
        }
    }
}

def installed() { initialize() }

def updated() {
    unschedule()
    initialize()
}

def initialize() {
    clearLegacyBridgeState()
    log.debug "Initializing HubSpace Device Manager v${appVersion()}"
    if (!settings.username || !settings.password) {
        log.info "HubSpace credentials are not configured (app v${appVersion()})"
        return
    }

    if (state.knownIds == null) state.knownIds = []
    if (state.lastPolled == null) state.lastPolled = [:]

    try {
        if (hasHubspaceSession()) {
            refreshTokenIfNeeded(false)
            discoverDevices()
        } else {
            log.info "HubSpace not connected yet (app v${appVersion()}). Use Connect to HubSpace."
        }
    } catch (Throwable t) {
        state.hsLastAuthError = t?.message
        log.warn "HubSpace initialization skipped (app v${appVersion()}): ${t?.message}"
    }

    schedule("*/${Math.max(15, (settings.pollSeconds ?: 30) as int)} * * * * ?", pollAll)
}

private discoverDevices() {
    refreshIndexAndDiscover()
}

def pollAll() {
    if (!settings.username || !settings.password) {
        return
    }
    if (state.lastPolled == null) state.lastPolled = [:]
    Long nowMs = now()
    Integer defaultSec = ((settings.pollSeconds ?: 30) as int)
    getChildDevices()?.each { cd ->
        Integer devSec = null
        try {
            def v = cd.getDataValue('devicePollSeconds')
            if (v && v.isInteger()) {
                devSec = (v as Integer)
            }
        } catch (ignored) {}
        int interval = (devSec ?: defaultSec)
        Long last = (state.lastPolled[cd.deviceNetworkId] ?: 0L) as Long
        if (last == 0L || (nowMs - last) >= (interval * 1000L)) {
            pollChild(cd)
            state.lastPolled[cd.deviceNetworkId] = nowMs
        }
    }
}

def pollChild(target) {
    def cd = childFromTarget(target)
    if (!cd) {
        return
    }
    String devId = cd.deviceNetworkId - 'hubspace-'
    try {
        Map stateData = hubspaceGetState(devId)
        updateFromState(cd, stateData)
        if (state.lastPolled == null) state.lastPolled = [:]
        state.lastPolled[cd.deviceNetworkId] = now()
    } catch (Throwable t) {
        state.hsLastAuthError = t?.message
        log.warn "HubSpace error polling device ${cd.displayName} (app v${appVersion()}): ${t?.message}"
    }
}

String driverForType(String t) {
    switch (t) {
        case 'light': return 'HubSpace Light'
        case 'switch': return 'HubSpace Switch'
        case 'fan': return 'HubSpace Fan'
        case 'ceiling-fan': return 'HubSpace Fan'
        case 'exhaust-fan': return 'HubSpace Exhaust Fan'
        case 'lock': return 'HubSpace Lock'
        case 'door-lock': return 'HubSpace Lock'
        case 'thermostat': return 'HubSpace Thermostat'
        case 'portable-air-conditioner': return 'HubSpace Portable AC'
        case 'valve': return 'HubSpace Valve'
        case 'water-timer': return 'HubSpace Valve'
        case 'security-system': return 'HubSpace Security System'
        case 'security-system-sensor': return 'HubSpace Security System Sensor'
        default: return 'HubSpace Device'
    }
}

def sendHsCommand(String devId, String cmd, Map args = [:]) {
    Map value = [functionClass: cmd, value: args.value]
    if (args.instance != null) {
        value.functionInstance = args.instance
    }
    try {
        hubspacePutState(devId, [value])
        log.debug "HubSpace command successful (app v${appVersion()}): ${cmd} ${args}"
        runIn(2, 'pollChild', [data: [dni: "hubspace-${devId}"]])
    } catch (Throwable t) {
        state.hsLastAuthError = t?.message
        log.error "HubSpace error sending command ${cmd} to device ${devId} (app v${appVersion()}): ${t?.message}"
    }
}

void refreshIndexAndDiscover() {
    Map<String, Map> disc = state.devices ?: [:]

    List allDevices = []
    try {
        log.info "[HubSpace] GET /metadevices (app v${appVersion()})"
        allDevices = hubspaceGetDevices()
        state.hsLastAuthError = null
    } catch (Throwable t) {
        state.hsLastAuthError = t?.message
        log.warn "Failed to retrieve devices from HubSpace API: ${t?.message}"
        return
    }

    allDevices.each { Map d ->
        String typeId = (d.typeId ?: d.type)?.toString()
        if (typeId && typeId != 'metadevice.device') {
            return
        }

        String id = (d.id ?: d.deviceId ?: d.metadeviceId ?: d.device_id)?.toString()
        String type = resolveDeviceClass(d)
        String name = resolveDeviceName(d, id)
        String deviceId = (d.deviceId ?: d.device_id)?.toString()
        List children = []
        try {
            if (d.children instanceof List) {
                children = d.children.collect { it?.toString() }.findAll { it }
            }
        } catch (ignored) {}
        if (!id || !type) {
            return
        }

        String dni = "hubspace-${id}"
        disc[dni] = [
            dni     : dni,
            id      : id,
            type    : type,
            name    : name ?: dni,
            deviceId: deviceId,
            children: children,
            raw     : d
        ]
    }

    state.devices = disc
}

def addDevicesPage() {
    log.debug 'addDevicesPage: begin'
    refreshIndexAndDiscover()

    Map devices = state.devices ?: [:]
    Map uninstalled = [:]
    Map<String, List<Map>> byDevId = [:].withDefault { [] }
    devices.each { String k, Map v ->
        String devId = (v.deviceId ?: '') as String
        if (devId) {
            byDevId[devId] << v
        }
    }

    Set<String> showDnis = [] as Set
    if (byDevId) {
        byDevId.each { String physId, List<Map> group ->
            if (group.size() > 1) {
                Map parent = group.find { (it.children instanceof List) && it.children.size() > 0 }
                if (!parent) parent = group.find { (it.type as String) == 'ceiling-fan' }
                if (!parent) parent = group[0]
                showDnis << (parent.dni as String)
            } else {
                showDnis << (group[0].dni as String)
            }
        }
    } else {
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

    dynamicPage(name: 'addDevicesPage',
        title: 'Add HubSpace Devices to Hubitat',
        nextPage: 'addDevStatus',
        install: false) {
        section() {
            paragraph 'Select devices to add. This page refreshes each time you open it.'
            input(name: 'selectedAddDevices', type: 'enum', title: "Devices to add (${uninstalled.size() ?: 0} available)",
                multiple: true, required: false, options: uninstalled)
        }
    }
}

def addDevStatus() {
    addDevices()
    def addMsg = new StringBuilder()
    def failMsg = new StringBuilder()
    if (!state.addedDevices) {
        addMsg << 'Added Devices: No devices added.'
    } else {
        addMsg << '<b>The following devices were installed:</b>\n'
        state.addedDevices.each { addMsg << "\t${it}\n" }
    }
    if (state.failedAdds) {
        failMsg << '<b>The following devices were not installed:</b>\n'
        state.failedAdds.each { failMsg << "\t${it}\n" }
    }
    dynamicPage(name: 'addDevStatus',
        title: 'Installation Status',
        nextPage: 'listDevices',
        install: false) {
        section() {
            paragraph addMsg.toString()
            paragraph failMsg.toString()
        }
    }
}

def removeDevicesPage() {
    log.debug 'removeDevicesPage: begin'
    Map devices = state.devices ?: [:]
    def installedDevices = [:]
    devices.keySet().sort().each { String dni ->
        def rec = devices[dni]
        def installed = getChildDevice(dni) ? 'Yes' : 'No'
        installedDevices[dni] = "${rec.name} - ${rec.type} [id: ${rec.id}, installed: ${installed}]"
    }

    dynamicPage(name: 'removeDevicesPage',
        title: 'Remove HubSpace Devices from Hubitat',
        nextPage: 'removeDevStatus',
        install: false) {
        section('Select Devices to Remove from Hubitat') {
            if (!installedDevices) {
                paragraph 'No installed HubSpace devices found.'
            } else {
                paragraph 'Select devices to remove. This will delete the child device(s) from Hubitat.'
                input(
                    name: 'selectedRemoveDevices',
                    type: 'enum',
                    title: "Devices to remove (${installedDevices.size()})",
                    multiple: true,
                    required: false,
                    options: installedDevices
                )
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
        okMsg << '<b>The following devices were removed:</b>\n'
        ok.each { okMsg << "\t${it}\n" }
    } else {
        okMsg << 'No devices were removed.'
    }
    if (fail) {
        failMsg << '<b>Failed to remove:</b>\n'
        fail.each { m -> failMsg << "\t${m}\n" }
    }
    dynamicPage(name: 'removeDevStatus',
        title: 'Removal Status',
        nextPage: 'mainPage',
        install: false) {
        section() {
            paragraph okMsg.toString()
            if (fail) {
                paragraph failMsg.toString()
            }
        }
    }
}

private void removeSelectedDevices() {
    state.removedDevices = []
    state.failedRemoves = []
    def picks = settings.selectedRemoveDevices ?: []
    if (!picks) {
        return
    }
    picks.each { String dni ->
        try {
            log.info "Attempting to remove child device ${dni}"
            def cd = getChildDevice(dni)
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
    app?.removeSetting('selectedRemoveDevices')
}

def addDevices() {
    log.info "addDevices: selected=${settings.selectedAddDevices}"
    state.addedDevices = []
    state.failedAdds = []
    def devices = state.devices ?: [:]
    (settings.selectedAddDevices ?: []).each { String dni ->
        def child = getChildDevice(dni)
        if (child) {
            return
        }
        def rec = devices[dni]
        if (!rec) {
            state.failedAdds << [dni: dni, reason: 'not in discovery']
            return
        }
        try {
            List created = []
            List children = (rec.children instanceof List) ? rec.children : []
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
                children.each { String cid ->
                    String cdni = "hubspace-${cid}"
                    def crec = devices[cdni]
                    if (!crec || getChildDevice(cdni)) {
                        return
                    }
                    String cdriver = driverForType(crec.type as String)
                    def cadded = addChildDevice(
                        'neerpatel/hubspace',
                        cdriver,
                        cdni,
                        [label: crec.name ?: cdni, isComponent: false]
                    )
                    cadded?.updateDataValue('hsType', crec.type as String)
                    if (rec.id) {
                        cadded?.updateDataValue('hsParentId', rec.id as String)
                    }
                    created << (crec.name ?: cdni)
                    log.info "Installed child ${crec.name} (${crec.type}) for parent ${rec.name}"
                    pauseExecution(150)
                }
            } else {
                String driver = driverForType(rec.type as String)
                def added = addChildDevice(
                    'neerpatel/hubspace',
                    driver,
                    dni,
                    [label: rec.name ?: dni, isComponent: false]
                )
                added?.updateDataValue('hsType', rec.type as String)
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
    app?.removeSetting('selectedAddDevices')
}

def listDevices() {
    log.debug 'listDevices'
    Map devices = state.devices ?: [:]
    Set<String> childDnis = [] as Set
    devices.each { String k, Map v -> (v.children ?: []).each { String cid -> childDnis << "hubspace-${cid}" } }
    List lines = []
    devices.keySet().sort().each { String dni ->
        def rec = devices[dni]
        if (childDnis.contains(dni)) {
            return
        }
        def installed = getChildDevice(dni) ? 'Yes' : 'No'
        lines << "<b>${rec.name} - ${rec.type}</b>: [id: ${rec.id}, installed: ${installed}]"
    }
    dynamicPage(name: 'listDevices',
        title: 'Discovered HubSpace Devices',
        nextPage: 'mainPage',
        install: false) {
        section() {
            paragraph "<b>Total HubSpace devices: ${devices.size() ?: 0}</b>\n<b>Alias: [id, Installed?]</b>"
            paragraph "<p style='font-size:14px'>${lines.join('\n')}</p>"
        }
    }
}

private updateFromState(cd, Map deviceData) {
    if (!deviceData) {
        return
    }

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

    def ignoreKeys = [
        'id', 'metadeviceId', 'name', 'type', 'friendlyName', 'friendly_name', 'device_class', 'description',
        'lastUpdateTime', 'accountId', 'deviceId', 'device_id', 'typeId'
    ] as Set
    deviceData.each { k, v ->
        if (!ignoreKeys.contains(k as String)) {
            processStateValue(cd, k as String, null, v)
        }
    }
}

private processStateValue(cd, String functionClass, String functionInstance, value) {
    switch (functionClass) {
        case 'values':
            break
        case 'power':
            def onoff = (value == 'on') ? 'on' : 'off'
            cd.sendEvent(name: 'switch', value: onoff)
            try {
                def hsType = (cd.getDataValue('hsType') ?: '').toLowerCase()
                if (hsType == 'valve' || hsType == 'water-timer') {
                    cd.sendEvent(name: 'valve', value: (onoff == 'on') ? 'open' : 'closed')
                }
            } catch (ignored) {}
            break
        case 'brightness':
            cd.sendEvent(name: 'level', value: (value as int))
            break
        case 'color-temperature':
            try {
                Integer ct
                if (value instanceof Number) {
                    ct = (value as int)
                } else {
                    def s = (value?.toString() ?: '').trim()
                    def m = (s =~ /(\d{3,5})/)
                    ct = m ? (m[0][1] as int) : null
                }
                if (ct != null) {
                    cd.sendEvent(name: 'colorTemperature', value: ct)
                }
            } catch (ignored) {}
            break
        case 'color-mode':
            def cm = (value as String)
            def hubitatMode = (cm == 'color') ? 'RGB' : ((cm == 'white') ? 'CT' : cm)
            cd.sendEvent(name: 'colorMode', value: hubitatMode)
            break
        case 'color-rgb':
            if (value instanceof Map) {
                def rgb = value
                def hsv = rgbToHSV(rgb.r as int, rgb.g as int, rgb.b as int)
                cd.sendEvent(name: 'hue', value: hsv.h)
                cd.sendEvent(name: 'saturation', value: hsv.s)
            }
            try { cd.updateDataValue('supportsColor', 'true') } catch (ignored) {}
            break
        case 'fan-speed':
            if (functionInstance == 'ac-fan-speed') {
                cd.sendEvent(name: 'thermostatFanMode', value: value as String)
            } else {
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

                String speedName = null
                if (maxLevels == null) {
                    try { speedName = cd?.currentValue('speed') ?: 'off' } catch (ignored) { speedName = 'off' }
                } else if (maxLevels >= 6) {
                    if (percent <= 16) speedName = 'low'
                    else if (percent <= 33) speedName = 'medium-low'
                    else if (percent <= 50) speedName = 'medium'
                    else if (percent <= 66) speedName = 'medium-high'
                    else if (percent <= 83) speedName = 'high'
                    else speedName = 'on'
                } else {
                    if (percent <= 33) speedName = 'low'
                    else if (percent <= 66) speedName = 'medium'
                    else speedName = 'high'
                }
                if (speedName) {
                    cd.sendEvent(name: 'speed', value: speedName)
                }
            }
            break
        case 'fan-direction':
            cd.sendEvent(name: 'direction', value: value as String)
            break
        case 'fan-reverse':
            cd.sendEvent(name: 'direction', value: (value as String))
            break
        case 'lock':
            cd.sendEvent(name: 'lock', value: value == 'locked' ? 'locked' : 'unlocked')
            break
        case 'motion-detection':
            cd.sendEvent(name: 'motion', value: value == 'motion-detected' ? 'active' : 'inactive')
            break
        case 'humidity-threshold-met':
            cd.sendEvent(name: 'humidity', value: value == 'above-threshold' ? 'active' : 'inactive')
            break
        case 'auto-off-timer':
            cd.sendEvent(name: 'autoOffTimer', value: value as int)
            break
        case 'timer-duration':
            cd.sendEvent(name: 'duration', value: value as int)
            break
        case 'motion-action':
            cd.sendEvent(name: 'motionAction', value: value as String)
            break
        case 'sensitivity':
            cd.sendEvent(name: 'sensitivity', value: value as String)
            break
        case 'alarm-status':
            cd.sendEvent(name: 'alarmStatus', value: value as String)
            cd.sendEvent(name: 'alarm', value: value as String)
            break
        case 'temperature':
            if (functionInstance == 'current-temp') {
                cd.sendEvent(name: 'temperature', value: value as float)
            } else if (functionInstance == 'cooling-target') {
                cd.sendEvent(name: 'coolingSetpoint', value: value as float)
            } else if (functionInstance == 'heating-target') {
                cd.sendEvent(name: 'heatingSetpoint', value: value as float)
            }
            break
        case 'mode':
            cd.sendEvent(name: 'thermostatMode', value: value as String)
            break
        case 'sleep':
            cd.sendEvent(name: 'sleepMode', value: value as String)
            break
        case 'available':
            cd.sendEvent(name: 'presence', value: value ? 'present' : 'not present')
            break
        case 'visible':
            cd.sendEvent(name: 'visible', value: value as String)
            break
        case 'direct':
            cd.sendEvent(name: 'direct', value: value as String)
            break
        case 'wifi-ssid':
            cd.sendEvent(name: 'ssid', value: value as String)
            break
        case 'wifi-rssi':
            try { cd.sendEvent(name: 'rssi', value: (value as int)) } catch (ignored) { cd.sendEvent(name: 'rssi', value: value as String) }
            break
        case 'wifi-steady-state':
            cd.sendEvent(name: 'wifiState', value: value as String)
            break
        case 'wifi-setup-state':
            cd.sendEvent(name: 'wifiSetupState', value: value as String)
            break
        case 'wifi-mac-address':
            cd.sendEvent(name: 'wifiMac', value: value as String)
            break
        case 'geo-coordinates':
            def lat = null
            def lon = null
            try {
                def gc = value?.get('geo-coordinates') ?: value
                lat = (gc?.latitude as BigDecimal)
                lon = (gc?.longitude as BigDecimal)
            } catch (ignored) {}
            if (lat != null && lon != null) {
                cd.sendEvent(name: 'latitude', value: lat)
                cd.sendEvent(name: 'longitude', value: lon)
                cd.sendEvent(name: 'location', value: "${lat},${lon}")
            } else {
                cd.sendEvent(name: 'location', value: value?.toString())
            }
            break
        case 'scheduler-flags':
            cd.sendEvent(name: 'schedulerFlags', value: value as String)
            break
        case 'error-flag':
            def inst = (functionInstance ?: 'error')?.toString().replace('-', '_')
            cd.sendEvent(name: inst, value: (value?.toString()))
            if (value == true || value?.toString() == 'true') {
                cd.sendEvent(name: 'healthStatus', value: 'error')
            }
            break
        case 'battery-level':
            cd.sendEvent(name: 'battery', value: value as int)
            break
        case 'switch':
            cd.sendEvent(name: 'switch', value: value ? 'on' : 'off')
            break
        default:
            log.debug "Unhandled state: ${functionClass}/${functionInstance} - ${value}"
            break
    }
}

private Map rgbToHSV(int r, int g, int b) {
    float rf = r / 255.0
    float gf = g / 255.0
    float bf = b / 255.0

    float max = Math.max(rf, Math.max(gf, bf))
    float min = Math.min(rf, Math.min(gf, bf))
    float delta = max - min

    float h = 0
    float s = 0
    float v = max

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

    [h: Math.round(h * 100 / 360), s: Math.round(s * 100), v: Math.round(v * 100)]
}
