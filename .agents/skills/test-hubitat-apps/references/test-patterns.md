# Hubitat Test Patterns

## Pure Logic Pattern

Keep decisions testable without Hubitat:

```groovy
boolean shouldTurnOn(Boolean motionActive, String mode, Integer lux, Integer threshold) {
    motionActive && mode != "Night" && lux < threshold
}
```

Then keep handlers thin:

```groovy
def motionHandler(evt) {
    if (shouldTurnOn(evt.value == "active", location.mode, currentLux(), settings.luxThreshold as Integer)) {
        switches.on()
    }
}
```

Test the pure method with plain Spock parameterized tests.

## Hubitat Script Pattern

Use `hubitat_ci` when the test must load a real app or driver script:

```groovy
class MotionAppSpec extends Specification {
    HubitatAppSandbox sandbox = new HubitatAppSandbox(new File("apps/motion-app.groovy"))

    def "basic validation"() {
        expect:
        sandbox.run()
    }
}
```

Mock the Hubitat API surface and selected devices. Verify:

- lifecycle method calls
- logs when useful
- `subscribe`, `schedule`, `runIn`, `unschedule`
- device commands such as `on`, `off`, `setLevel`
- `sendEvent` calls in drivers
- `state` changes

## Scenario Pattern

Represent behavior as test data first:

```yaml
name: no command when mode is Night
given:
  motion: active
  mode: Night
  lux: 10
  threshold: 50
expect:
  commands: []
```

Use scenarios to avoid losing behavior during migration. Each scenario should identify:

- input state
- incoming event or scheduled callback
- expected command/event/state change
- expected no-op behavior when applicable

## What To Mock

- Devices selected in preferences
- `location.mode`
- `settings`
- `state`
- logging
- Hubitat scheduling/subscription methods
- HTTP methods only at the boundary, with canned responses

## What Not To Mock Away

- Decision logic
- Preference names and defaults
- Capability/command names
- Attribute names and event values
- State keys used across callbacks
