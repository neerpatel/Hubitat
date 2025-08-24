---
applyTo: "./Hubitat/**/*.groovy, ./bridge-node/**/*.js"
---

When you change the version of a Groovy App or Driver, immediately update `Hubitat/{Integration}/release-notes.md` in the same integration folder or `bridge-node/release-notes.md`. The release notes must document what changed, why it changed, and what impact the change may have on users or automations.

**IMPORTANT**: The release-notes.md file is a running log of ALL changes over time. Always add new entries at the TOP of the file, maintaining reverse chronological order (newest first). Never replace existing entries - only add new ones above the previous releases.

Required release-notes.md structure:

```markdown
# Release Notes

## Version X.X.X (YYYY-MM-DD) - **NEW ENTRY GOES HERE**

### 🎉 New Features

- **Feature Name**: Brief description of what was added and why it's beneficial
- **Another Feature**: Description with user impact

### 🔧 Improvements

- **Performance**: Description of performance enhancements
- **UI/UX**: User interface improvements
- **Code Quality**: Internal improvements (if they affect user experience)

### 🐛 Bug Fixes

- **Issue Description**: What was broken and how it was fixed
- **Another Fix**: Clear description of the problem and resolution

### ⚠️ Breaking Changes

- **Change Description**: What changed, why it was necessary, and migration steps
- **Impact**: Who is affected and what they need to do

### 📋 Technical Details

- **Dependencies**: Any new or updated dependencies
- **Requirements**: System or platform requirement changes
- **API Changes**: For apps/drivers that expose APIs

### 🚀 Upgrade Instructions

1. Step-by-step upgrade process
2. Any manual configuration changes required
3. Testing recommendations after upgrade

### 📝 Notes

- Additional context or limitations
- Known issues (if any)
- Future deprecation warnings

---

## Version X.X.X-1 (YYYY-MM-DD) - **PREVIOUS RELEASE**

### 🐛 Bug Fixes

- **Previous Fix**: Description of earlier bug fix

---

## Version X.X.X-2 (YYYY-MM-DD) - **OLDER RELEASE**

### 🎉 New Features

- **Earlier Feature**: Description of feature from previous version

---

<!-- Continue with older versions in reverse chronological order -->
```

## Running Log Guidelines

### File Maintenance Rules

1. **NEVER delete or modify existing release entries** - the file is a historical record
2. **ALWAYS add new releases at the TOP** of the file (after the "# Release Notes" header)
3. **PRESERVE all previous versions** in reverse chronological order (newest to oldest)
4. **Use horizontal rules (---)** to clearly separate each version
5. **Keep consistent formatting** across all entries for readability

### Adding a New Release

When creating a new release entry:

1. **Position**: Add immediately after the "# Release Notes" header
2. **Format**: Use the complete template structure shown above
3. **Separation**: Add a horizontal rule (---) after your new entry
4. **Existing Content**: Leave all previous releases unchanged below your new entry

### Example of Proper File Structure

```markdown
# Release Notes

## Version 2.1.0 (2024-01-15) - **LATEST RELEASE**

### 🎉 New Features

- **Auto-Discovery**: New automatic device discovery feature

---

## Version 2.0.5 (2024-01-10)

### 🐛 Bug Fixes

- **Temperature Sensor**: Fixed intermittent reading errors

---

## Version 2.0.4 (2024-01-05)

### 🔧 Improvements

- **Performance**: Reduced memory usage by 20%

---

## Version 2.0.0 (2024-01-01)

### ⚠️ Breaking Changes

- **API Changes**: Updated device communication protocol

---

<!-- All previous versions continue below... -->
```

### Integration-Specific Notes

- **Hubitat Apps/Drivers**: Focus on hub functionality and device behavior
- **Bridge Node**: Focus on API changes and connectivity improvements
- Always mention if a hub reboot or device re-pairing is required

### Historical Value

The running log format provides several benefits:

- **Complete change history** for troubleshooting and debugging
- **Version comparison** to understand what changed between releases
- **User reference** to see when specific features were added or bugs were fixed
- **Developer context** for understanding the evolution of the integration
- **Rollback guidance** when users need to understand what changed between versions

Remember: Each release-notes.md file becomes a permanent historical record of the integration's development and should never lose information from previous releases.
