# Release Notes

## Version 0.2.4 (2025-08-24)

### 🎉 Initial Release Features

- **HubSpace Device Manager**: Complete device management app for Hubitat integration
  - Automatic device discovery from HubSpace cloud accounts
  - Secure authentication flow through bridge server
  - Device state polling and real-time updates
  - Support for adding/removing devices dynamically
  - Health monitoring and diagnostics

- **Device Driver Support**: Full driver collection for HubSpace devices
  - **Smart Lights**: On/off, dimming (0-100%), color temperature control, full RGB color support
  - **Ceiling Fans**: Variable speed control, direction control, integrated lighting
  - **Smart Switches**: Basic on/off control with state feedback
  - **Exhaust Fans**: Speed control and power management
  - **Locks**: Lock/unlock control with status reporting
  - **Portable AC Units**: Temperature control and mode settings
  - **Security Systems**: Arm/disarm with sensor monitoring
  - **Security Sensors**: Motion detection and contact sensor support
  - **Thermostats**: Temperature control with heating/cooling modes
  - **Valves**: Open/close control for water and gas applications

### 🔧 Core Capabilities

- **Bridge Integration**: Seamless communication with Node.js bridge server
- **OAuth Authentication**: Secure credential handling without local storage
- **State Synchronization**: Real-time device state updates from HubSpace cloud
- **Command Routing**: Hubitat device commands mapped to HubSpace function classes
- **Error Handling**: Comprehensive error logging and recovery mechanisms
- **Dashboard Compatibility**: Full integration with Hubitat dashboards and automations

### 📋 Technical Details

- **Device Manager Version**: 0.1.2
- **Driver Versions**: 0.1.2 across all device types
- **Function Class Support**: power, brightness, color-temperature, color-rgb, fan-speed, direction
- **Polling**: Configurable polling interval (default 30 seconds)
- **Session Management**: Automatic session handling and token refresh

### 🚀 Installation Requirements

- Running HubSpace bridge server (bridge-node component)
- Valid HubSpace account credentials
- Network connectivity between Hubitat hub and bridge server
- Hubitat hub with custom driver support

### 📝 Notes

- All device drivers include telemetry attributes (wifi, rssi, battery status)
- Comprehensive logging with version information for diagnostics
- No credentials stored locally - all authentication handled through bridge
- Compatible with Hubitat dashboard tiles and rule engine

---