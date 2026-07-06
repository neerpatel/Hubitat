---
name: model-hubitat-devices
description: Design Hubitat device models, drivers, capabilities, attributes, commands, fingerprints, component/child devices, virtual devices, and app device inputs. Use when Codex is asked to choose Hubitat capabilities, create or review driver metadata, expose device state, map real or virtual devices to Hubitat, design parent/child drivers or parent apps, or convert Home Assistant entities or SmartThings device handlers into Hubitat device abstractions.
---

# Model Hubitat Devices

## Workflow

1. Describe the real-world thing being modeled.
2. Choose standard Hubitat capabilities before inventing custom commands or attributes.
3. Decide whether the artifact is a driver, child driver, app-created child device, or virtual device.
4. Define attributes as observable state and commands as actions.
5. Use `references/capability-modeling.md` when selecting capabilities or reviewing metadata.
6. Use `code-hubitat-groovy` for the actual Groovy implementation.

## Modeling Rules

- Prefer standard capabilities because apps use them for device selectors and automation compatibility.
- Add custom attributes only when no standard capability exposes the needed state.
- Add custom commands only for actions that are not covered by standard capabilities.
- Make attributes stable, small, and automation-friendly.
- Use enumerated values where Hubitat capability conventions expect them.
- For virtual devices, simulate resulting attributes immediately after commands when that is the intended behavior.
- For physical or API-backed devices, report real observed state where possible.
- For multi-endpoint devices, use parent/child patterns instead of cramming unrelated endpoints into one device.
- Use app-created child devices when an integration discovers multiple logical devices from one account, bridge, or API.

## Driver Metadata Shape

```groovy
metadata {
    definition(name: "Example Switch", namespace: "example", author: "Example") {
        capability "Switch"
        capability "Refresh"

        attribute "lastCheckin", "string"
        command "sync"
    }

    preferences {
        input name: "debugLogging", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}
```

## Design Checklist

- Standard capabilities selected first.
- Required commands for each capability are implemented.
- Required attributes for each capability are emitted with `sendEvent`.
- App inputs reference capabilities, not concrete driver names.
- Parent/child boundaries match user-visible devices.
- Custom attributes and commands are documented in code comments or release notes.
- Names are stable enough for existing automations to survive updates.

## References

- `references/capability-modeling.md`: standards, examples, and official docs links for capabilities, attributes, commands, and parent/child devices.
