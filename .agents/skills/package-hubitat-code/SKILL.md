---
name: package-hubitat-code
description: Prepare Hubitat Elevation Groovy apps and drivers for publishing, installation, versioning, import URLs, release notes, and Hubitat Package Manager manifests. Use when Codex is asked to package, release, publish, distribute, version, create HPM manifests, create install instructions, prepare a Hubitat app or driver for users, or review pre-release quality for Hubitat code.
---

# Package Hubitat Code

## Workflow

1. Identify release scope: app, driver, parent/child set, bundle, or HPM package.
2. Verify code quality and behavior with `code-hubitat-groovy` and `test-hubitat-apps`.
3. Normalize metadata: name, namespace, author, description, import URL, and version comments if used.
4. Prepare install/update instructions.
5. For Hubitat Package Manager, create or update package and repository manifests.
6. Read `references/release-and-hpm.md` before generating manifests or publishing guidance.

## Release Rules

- Prefer SemVer for package or component versions.
- Do not mix package-level and per-app/driver versioning styles within the same HPM package.
- Keep raw file URLs stable if using import URLs or HPM.
- Publish parent apps/drivers before children when install order matters.
- Include migration notes for renamed preferences, changed state keys, changed child device names, or removed commands.
- Keep debug logging disabled by default.
- Include minimum Hubitat platform assumptions when relevant.

## Pre-Release Checklist

- Code saves successfully in Hubitat.
- Tests and dev-hub smoke checks pass.
- Metadata is accurate.
- Required app/driver files are included.
- HPM manifest references raw URLs, not rendered HTML pages.
- Release notes identify new features, fixes, breaking changes, and migration steps.
- Install instructions tell users where to add app code, driver code, bundles, or HPM package sources.

## References

- `references/release-and-hpm.md`: release structure, HPM manifest guidance, and official/community docs links.
