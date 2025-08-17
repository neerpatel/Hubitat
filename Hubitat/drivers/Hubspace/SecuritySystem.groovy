metadata {
  definition(name: "HubSpace Security System", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "SecurityKeypad"
    capability "Refresh"
    capability "PresenceSensor"
    capability "Alarm"
    
    attribute "securitySystemStatus", "enum", ["disarmed", "armed home", "armed away", "armed night"]
    attribute "alarmStatus", "enum", ["off", "strobe", "siren", "both"]
    
    command "armHome"
    command "armAway"
    command "armNight"
    command "disarm"
    command "setAlarmStatus", [[name:"status", type:"ENUM", constraints:["off", "strobe", "siren", "both"]]]
    // Standard Alarm capability shortcuts
    command "siren"
    command "strobe"
    command "both"
    command "off"
  }
}

def initialize() {
  log.debug "Initializing HubSpace Security System"
}

def refresh() { 
  parent.pollChild(device) 
}

def armHome() {
  log.info "Arming home for ${device.displayName}"
  parent.sendHsCommand(id(), "security-system-mode", [value: "home"])
}

def armAway() {
  log.info "Arming away for ${device.displayName}"
  parent.sendHsCommand(id(), "security-system-mode", [value: "away"])
}

def armNight() {
  log.info "Arming night for ${device.displayName}"
  parent.sendHsCommand(id(), "security-system-mode", [value: "night"])
}

def disarm() {
  log.info "Disarming ${device.displayName}"
  parent.sendHsCommand(id(), "security-system-mode", [value: "disarmed"])
}

def setAlarmStatus(status) {
  log.info "Setting alarm status to ${status} for ${device.displayName}"
  parent.sendHsCommand(id(), "alarm-status", [value: status])
}

// Alarm capability implementations
def siren() { setAlarmStatus('siren') }
def strobe() { setAlarmStatus('strobe') }
def both()  { setAlarmStatus('both') }
def off()   { setAlarmStatus('off') }

private id() { 
  device.deviceNetworkId - "hubspace-" 
}
