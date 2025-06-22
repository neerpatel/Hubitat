/*
 *  Hubspace Device Manager
 *  Provides connection to Hubspace cloud API and controls Hubspace devices.
 *  This driver is inspired by https://github.com/jdeath/Hubspace-Homeassistant
 *  and structured similar to drivers in https://github.com/DaveGut/HubitatActive.
 */

metadata {
    definition(name: "Hubspace Device Manager", namespace: "community", author: "Codex") {
        capability "Refresh"
        capability "Initialize"
        attribute "lastCheckin", "string"
        command "listDevices"
        command "turnOn", ["string"]
        command "turnOff", ["string"]
    }
    preferences {
        input name: "username", type: "string", title: "Hubspace Username", required: true
        input name: "password", type: "password", title: "Hubspace Password", required: true
    }
}

void installed() {
    log.info "Installed Hubspace Device Manager"
    initialize()
}

void updated() {
    log.info "Updated Hubspace Device Manager"
    initialize()
}

void initialize() {
    state.token = null
    refresh()
}

void refresh() {
    authenticate()
    updateLastCheckin()
}

private void updateLastCheckin() {
    sendEvent(name: "lastCheckin", value: new Date().toString())
}

private void authenticate() {
    if (!username || !password) {
        log.warn "Hubspace credentials not set"
        return
    }
    try {
        def params = [
            uri: "https://hubspaceconnect.com/api/auth/login",
            contentType: "application/json",
            body: ["username": username, "password": password]
        ]
        httpPost(params) { resp ->
            if (resp.status == 200 && resp.data?.token) {
                state.token = resp.data.token
                log.debug "Obtained Hubspace auth token"
            } else {
                log.warn "Failed to authenticate to Hubspace"
            }
        }
    } catch (Exception e) {
        log.error "Hubspace authentication error: ${e.message}"
    }
}

void listDevices() {
    if (!state.token) {
        log.warn "Not authenticated"
        return
    }
    try {
        def params = [
            uri: "https://hubspaceconnect.com/api/devices",
            headers: ["Authorization": "Bearer ${state.token}"],
            contentType: "application/json"
        ]
        httpGet(params) { resp ->
            if (resp.status == 200) {
                log.info "Hubspace devices: ${resp.data}"
            } else {
                log.warn "Failed to fetch devices: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Device list error: ${e.message}"
    }
}

void turnOn(String deviceId) {
    sendDeviceCommand(deviceId, true)
}

void turnOff(String deviceId) {
    sendDeviceCommand(deviceId, false)
}

private void sendDeviceCommand(String deviceId, Boolean powerState) {
    if (!state.token) {
        log.warn "Not authenticated"
        return
    }
    try {
        def params = [
            uri: "https://hubspaceconnect.com/api/device/${deviceId}/state",
            headers: ["Authorization": "Bearer ${state.token}"],
            contentType: "application/json",
            body: ["power": powerState]
        ]
        httpPost(params) { resp ->
            if (resp.status == 200) {
                log.info "Device ${deviceId} power set to ${powerState}"
            } else {
                log.warn "Failed to control device: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Device command error: ${e.message}"
    }
}
