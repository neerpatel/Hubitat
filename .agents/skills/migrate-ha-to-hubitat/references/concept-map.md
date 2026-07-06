# Home Assistant To Hubitat Concept Map

Use this mapping while translating behavior. Prefer preserving intent over copying structure.

| Home Assistant | Hubitat |
|---|---|
| Custom integration | App, driver, or parent app/driver plus child devices |
| Entity | Device with capabilities/attributes, often a child device |
| Platform entity (`sensor`, `switch`, `light`) | Driver implementing matching capabilities |
| Service call | Device command or app method |
| Automation trigger | `subscribe()`, schedule, app lifecycle callback, or endpoint |
| Automation condition | Guard clause in an event/scheduled handler |
| Automation action | Device command, state update, notification, or child-device operation |
| Config flow/options flow | `preferences` pages and `settings` |
| `hass.data` | `state`, `atomicState`, child device data, or app-level fields |
| Event bus listener | `subscribe(location, ...)`, `subscribe(device, attribute, handler)` |
| Time pattern trigger | `schedule`, `runIn`, `runEvery*` |
| Helpers/input booleans | Preferences or virtual devices |
| Device registry | Hubitat Devices page plus app/driver child-device creation |
| Availability | Attribute state, health/check-in event, or explicit error logging |
| Coordinator/update method | Scheduled poll, event handler, command handler, or refresh command |

## Translation Heuristics

- If users configure it, make it a preference.
- If another automation should interact with it, expose it as a device capability/attribute.
- If it is internal bookkeeping, use `state`.
- If HA code calls a service, look for the matching Hubitat device command.
- If HA code listens for state changes, use `subscribe()` to device attributes.
- If HA creates many entities from one account/API/device, create child devices.
- If HA uses async polling/coordinators, use Hubitat schedules or `refresh()` cautiously.
- If HA relies on startup reload behavior, implement `installed()`, `updated()`, and optionally `initialize()`.

## Common Pitfalls

- Do not assume HA entity IDs map cleanly to Hubitat device names.
- Do not store large API payloads in `state`.
- Do not leave schedules/subscriptions duplicated after preferences change.
- Do not model everything as a custom command when standard capabilities exist.
- Do not port Python async structure directly; Hubitat script execution is event/callback oriented.
