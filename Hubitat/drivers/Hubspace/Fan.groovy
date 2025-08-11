metadata {
    definition(name: 'HubSpace Fan', namespace: 'neerpatel/hubspace', author: 'Neer Patel') {
        capability 'Initialize'
        capability 'Switch'
        capability 'FanControl'
        capability 'Refresh'
    }
}
def initialize() { }
def refresh() { parent.pollChild(device) }
def on()  { parent.sendHsCommand(id(), 'turn_on') }
def off() { parent.sendHsCommand(id(), 'turn_off') }
def setSpeed(s) { parent.sendHsCommand(id(), 'set_speed', [speed: s]) }
private id() { device.deviceNetworkId - 'hubspace-' }
