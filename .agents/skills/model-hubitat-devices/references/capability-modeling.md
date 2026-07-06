# Hubitat Capability And Device Modeling

## Official References

- Capability object: https://docs2.hubitat.com/developer/capability-object
- Driver capability list: https://docs2.hubitat.com/pt-br/developer/driver/capability-list
- Driver overview: https://docs2.hubitat.com/pt-br/developer/driver/overview
- Parent/child drivers: https://docs2.hubitat.com/en/developer/driver/parent-child-drivers

## Capability Selection

Capabilities define the standard commands and attributes a device exposes. Choose them based on how other apps should interact with the device.

Common examples:

| Device behavior | Likely capability |
|---|---|
| On/off control | `Switch` |
| Brightness | `SwitchLevel` |
| Color temperature | `ColorTemperature` |
| RGB color | `ColorControl` |
| Motion state | `MotionSensor` |
| Contact open/closed | `ContactSensor` |
| Temperature reading | `TemperatureMeasurement` |
| Battery reporting | `Battery` |
| Manual refresh | `Refresh` |
| Startup reconnect/init | `Initialize` |

## Attribute Rules

- Attributes represent observable state.
- Attribute values should match capability expectations.
- Custom attributes should be simple: string, number, enum-like strings, or booleans when appropriate.
- Drivers should emit attribute changes through `sendEvent`.

## Command Rules

- Commands represent actions.
- Implement all commands required by declared capabilities.
- Custom commands should have clear names and predictable side effects.
- Commands for virtual devices may immediately emit the resulting state.

## Parent/Child Patterns

Use child devices when:

- one physical device exposes independent endpoints
- one bridge/account/API discovers many logical devices
- users should automate each child independently
- each child has its own capabilities and attributes

Keep parent code responsible for shared communication and discovery. Keep child code focused on translating Hubitat commands/events for the child endpoint.
