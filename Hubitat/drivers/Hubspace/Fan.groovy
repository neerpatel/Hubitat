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
def on()  { parent.sendHsCommand(id(), 'power', [value: 'on']) }
def off() { parent.sendHsCommand(id(), 'power', [value: 'off']) }
def setSpeed(s) { parent.sendHsCommand(id(), 'fan-speed', [value: s]) }
private id() { device.deviceNetworkId - 'hubspace-' }