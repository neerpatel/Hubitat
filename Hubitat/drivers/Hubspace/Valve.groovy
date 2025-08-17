metadata {
  definition(name: "HubSpace Valve", namespace: "neerpatel/hubspace", author: "Neer Patel") {
    capability "Initialize"
    capability "Valve"
    capability "Switch"
    capability "Refresh"
    capability "PresenceSensor"
    capability "Battery"
    
    attribute "duration", "number"
    attribute "remainingTime", "number"
    
    command "setDuration", [[name:"duration", type:"NUMBER", description:"Timer duration in minutes"]]
  }
}

def initialize() {
  log.debug "Initializing HubSpace Valve"
  refresh()
}

def refresh() {
  parent.pollChild(device)
}

def open() { 
  log.info "Opening valve ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "on"]) 
}

def close() { 
  log.info "Closing valve ${device.displayName}"
  parent.sendHsCommand(id(), "power", [value: "off"]) 
}

def on() { open() }
def off() { close() }

def setDuration(duration) {
  log.info "Setting duration to ${duration} minutes for ${device.displayName}"
  parent.sendHsCommand(id(), "timer-duration", [value: duration as int])
}

private id() {
  device.deviceNetworkId - "hubspace-"
}
