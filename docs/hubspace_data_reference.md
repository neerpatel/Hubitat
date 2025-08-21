# HubSpace Metadevices Data Reference

This document summarizes your collected HubSpace/Afero metadevices payload, mapping device classes to their observed function classes, value types, and example values. It also suggests Hubitat capability mappings and PUT payload shapes.

## Inventory
- Devices analyzed: 21
- Classes observed:
  - light: 7
  - ceiling-fan: 7
  - fan: 7

## Common Function Classes (across device classes)
- `power` in 3 class(es) → Hubitat: Switch
- `wifi-ssid` in 3 class(es)
- `wifi-rssi` in 3 class(es)
- `wifi-steady-state` in 3 class(es)
- `wifi-setup-state` in 3 class(es)
- `wifi-mac-address` in 3 class(es)
- `geo-coordinates` in 3 class(es)
- `scheduler-flags` in 3 class(es)
- `available` in 3 class(es)
- `visible` in 3 class(es)
- `direct` in 3 class(es)
- `ble-mac-address` in 3 class(es)
- `color-temperature` in 1 class(es) → Hubitat: ColorTemperature
- `brightness` in 1 class(es) → Hubitat: SwitchLevel
- `error-flag` in 1 class(es)
- `timer` in 1 class(es)
- `toggle` in 1 class(es)
- `fan-speed` in 1 class(es) → Hubitat: FanControl
- `fan-reverse` in 1 class(es)

## Per-Class Details
### ceiling-fan
- `error-flag`: on 7 device(s), 14 state entr(y/ies)
  - Value types: boolean:14
  - Instances: acz-error, storage-error
  - Sample: false

- `power`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Instances: primary
  - Sample: "on"
  - Hubitat: Switch (switch)

- `wifi-ssid`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "IdiOT"

- `wifi-rssi`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: -67

- `wifi-steady-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-setup-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0be280"

- `geo-coordinates`: on 7 device(s), 7 state entr(y/ies)
  - Value types: object:6, null:1
  - Instances: system-device-location
  - Sample: {"geo-coordinates": {"latitude": "32.78604125976562", "longitude": "-97.06889343261719"}}

- `scheduler-flags`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: 0

- `available`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `visible`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `direct`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `ble-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0c14f2"

### fan
- `timer`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Instances: fan-power
  - Sample: 0

- `toggle`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Instances: comfort-breeze
  - Sample: "disabled"

- `fan-speed`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Instances: fan-speed
  - Sample: "fan-speed-6-100"
  - Hubitat: FanControl (speed)

- `power`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Instances: fan-power
  - Sample: "on"
  - Hubitat: Switch (switch)

- `wifi-ssid`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "IdiOT"

- `wifi-rssi`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: -67

- `wifi-steady-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-setup-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0be280"

- `geo-coordinates`: on 7 device(s), 7 state entr(y/ies)
  - Value types: object:6, null:1
  - Instances: system-device-location
  - Sample: {"geo-coordinates": {"latitude": "32.78611755371094", "longitude": "-97.06877136230469"}}

- `scheduler-flags`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: 0

- `available`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `visible`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `direct`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `ble-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0c14f2"

- `fan-reverse`: on 4 device(s), 4 state entr(y/ies)
  - Value types: string:4
  - Instances: fan-reverse
  - Sample: "forward"

### light
- `color-temperature`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "3500K"
  - Hubitat: ColorTemperature (colorTemperature)

- `brightness`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: 97
  - Hubitat: SwitchLevel (level)

- `power`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Instances: light-power
  - Sample: "off"
  - Hubitat: Switch (switch)

- `wifi-ssid`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "IdiOT"

- `wifi-rssi`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: -67

- `wifi-steady-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-setup-state`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "connected"

- `wifi-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0be280"

- `geo-coordinates`: on 7 device(s), 7 state entr(y/ies)
  - Value types: object:6, null:1
  - Instances: system-device-location
  - Sample: {"geo-coordinates": {"latitude": "32.78611755371094", "longitude": "-97.06877136230469"}}

- `scheduler-flags`: on 7 device(s), 7 state entr(y/ies)
  - Value types: number:7
  - Sample: 0

- `available`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `visible`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `direct`: on 7 device(s), 7 state entr(y/ies)
  - Value types: boolean:7
  - Sample: true

- `ble-mac-address`: on 7 device(s), 7 state entr(y/ies)
  - Value types: string:7
  - Sample: "d4d4da0c14f2"

## PUT Payload Shape
All updates flow through PUT `/v1/accounts/{accountId}/metadevices/{deviceId}/state` with body:
```
{ "metadeviceId": "<deviceId>", "values": [ { "functionClass": "<class>", "functionInstance": null, "value": <scalar-or-object> } ] }
```
Notes:
- Timestamps are optional; HubSpace accepts server-side TS.
- For boolean-like toggles, some devices use strings `on|off` rather than true/false.
- Color RGB expects an object `{r,g,b}`; thermostat targets use distinct functionInstances.

## Next Data Gaps to Explore
- If some classes above show only `unknown` or few function classes, collect additional samples with the latest script.
- For devices with object-valued states (e.g., `color-rgb`), capture a couple of values to confirm full shape.
- Consider fetching versions for all deviceIds to correlate firmware with capabilities.

### Diagnostics
- Malformed state containers: 0
- Malformed state entries: 0