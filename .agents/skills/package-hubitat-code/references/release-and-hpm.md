# Hubitat Release And HPM Guidance

## References

- Install custom apps: https://docs2.hubitat.com/how-to/install-custom-apps
- App definition: https://docs2.hubitat.com/developer/app/definition
- Driver overview: https://docs2.hubitat.com/pt-br/developer/driver/overview
- Hubitat Package Manager developer docs: https://hubitatpackagemanager.hubitatcommunity.com/devs1.html

## Release Structure

For a simple release:

- app `.groovy` files
- driver `.groovy` files
- install/update notes
- changelog or release notes
- raw URLs for import or package manifests

For HPM:

- package manifest listing apps and drivers in the package
- repository manifest listing packages
- stable raw URLs for each file
- consistent versioning

## HPM Notes

Hubitat Package Manager expects package metadata and source file URLs. Follow its docs closely when generating JSON manifests.

Recommended practices:

- Use SemVer.
- Version either the package as a whole or each app/driver, but do not mix styles in one package.
- Keep package names stable.
- Use raw GitHub URLs or equivalent raw file URLs.
- Include every required parent/child app and driver.
- Validate JSON before release.

## Install Instruction Shape

For manual installs, include:

1. Enable advanced/developer options if needed.
2. Add driver code under Drivers Code.
3. Add app code under Apps Code.
4. Create devices or add the user app.
5. Configure preferences.
6. Save, then review logs.

For HPM installs, include:

1. Install Hubitat Package Manager if needed.
2. Add the package repository if it is not listed.
3. Install or update the package.
4. Configure app/device preferences.
