# Hubitat Release Checklist

## Local Checks

- Spock/unit tests pass.
- `hubitat_ci` script validation passes where used.
- Test cases cover lifecycle callbacks and primary handlers.
- Metadata, capabilities, commands, attributes, and preferences are validated.
- No accidental debug-only code remains.

## Dev Hub Checks

- App/driver code saves successfully.
- New install succeeds.
- Preference update succeeds.
- Uninstall or driver replacement does not leave stale schedules or subscriptions.
- Virtual-device smoke test passes.
- Real-device or real-API smoke test passes when required.

## Publishing Checks

- `definition` or driver metadata has correct name, namespace, author, and import URL if applicable.
- Version is updated consistently.
- README or release notes outside the skill/app code describe setup, permissions, and limitations.
- Logs are quiet by default with an option for debug logging.
- Breaking changes and migration steps are called out.
