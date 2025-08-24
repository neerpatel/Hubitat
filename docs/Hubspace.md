# 🏠 HubSpace Integration for Hubitat 🌉

### Seamless HubSpace Device Integration for Hubitat

![Node.js](https://img.shields.io/badge/Node.js-18+-green) ![TypeScript](https://img.shields.io/badge/TypeScript-Ready-blue) ![Hubitat](https://img.shields.io/badge/Hubitat-Compatible-orange) ![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue)

🌟 **Bridge your Hubitat hub with HubSpace cloud devices effortlessly!**  
Control lights, fans, and smart devices through a secure OAuth proxy that connects your local Hubitat hub with the HubSpace cloud ecosystem.

---

## 📋 Table of Contents

| Section | Description |
|---------|-------------|
| 🏗️ [System Architecture](#-system-architecture) | Overview of bridge components and data flow |
| ✨ [Features](#-features) | Key capabilities and integrations |
| 🚀 [Quick Start](#-quick-start) | Get running in minutes |
| ⚙️ [Configuration](#-configuration) | Setup and customization options |
| 🛠️ [Development](#-development) | Contributing and local development |
| 🏠 [Hubitat Integration](#-hubitat-integration) | Device manager and driver details |
| 🔧 [Troubleshooting](#-troubleshooting) | Common issues and solutions |

---

## 🏗️ System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Hubitat Hub   │◄──►│   Bridge Server │◄──►│  HubSpace Cloud │
│                 │    │   (Node.js)     │    │   (OAuth API)   │
│  ┌─────────────┐│    │                 │    │                 │
│  │   Device    ││    │  ┌─────────────┐│    │  ┌─────────────┐│
│  │  Manager    ││    │  │   Express   ││    │  │   Devices   ││
│  │             ││    │  │   Server    ││    │  │  & State    ││
│  └─────────────┘│    │  └─────────────┘│    │  └─────────────┘│
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

**Data Flow:**
1. Hubitat Device Manager authenticates with Bridge Server
2. Bridge Server handles OAuth flow with HubSpace Cloud
3. Real-time device state synchronization
4. Secure command relay for device control

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔐 **Secure OAuth Proxy** | PKCE-enabled authentication with HubSpace cloud |
| 🌐 **RESTful API** | Clean endpoints for authentication and device control |
| ⚡ **Real-time Sync** | Live device state updates and command relay |
| 🏠 **Native Hubitat Integration** | Groovy drivers with full Hubitat ecosystem support |
| 🔄 **Session Management** | Automatic token refresh and session persistence |
| 🚀 **Easy Deployment** | One-command setup with PM2 process management |
| 🛡️ **Security First** | No credential storage, secure token handling |
| 📱 **Multi-Device Support** | Lights, fans, switches, and more device types |
| 🔧 **Extensible Architecture** | Easy to add new device types and providers |
| 📊 **Dashboard Ready** | Full compatibility with Hubitat dashboards |

---

## 🚀 Quick Start

### 🎯 Get your HubSpace bridge running in minutes!

| Step | Action | Command/Description |
|------|--------|-------------------|
| 1️⃣ | **Clone Repository** | `git clone https://github.com/neerpatel/Hubitat.git` |
| 2️⃣ | **Install Dependencies** | `cd Hubitat/bridge-node && npm install` |
| 3️⃣ | **Start Bridge Server** | `npm start` (dev) or `pm2 start ecosystem.config.js` (prod) |
| 4️⃣ | **Install Hubitat Driver** | Import `Hubitat/Hubspace/HubspaceDeviceManager.groovy` |
| 5️⃣ | **Configure Bridge URL** | Set bridge URL in device manager preferences |
| 6️⃣ | **Add HubSpace Credentials** | Enter your HubSpace username/password in device settings |
| 7️⃣ | **Discover Devices** | Run device discovery to import your HubSpace devices |

### 🎉 That's it! Your HubSpace devices are now accessible in Hubitat!

---

## ⚙️ Configuration

### Bridge Server Configuration

| Environment Variable | Description | Default |
|----------------------|-------------|---------|
| `PORT` | Server listening port | `3000` |
| `NODE_ENV` | Environment mode | `development` |

### Hubitat Device Manager Settings

| Setting | Description | Required |
|---------|-------------|----------|
| **HubSpace Username** | Your HubSpace account email | ✅ Yes |
| **HubSpace Password** | Your HubSpace account password | ✅ Yes |
| **Bridge Server URL** | URL of your running bridge server | ✅ Yes |
| **Polling Interval** | Device state refresh frequency (seconds) | No (default: 30) |
| **Enable Debug Logging** | Detailed logging for troubleshooting | No |

### Example Configuration

```
Bridge Server URL: http://192.168.1.100:3000
HubSpace Username: your-email@domain.com
HubSpace Password: your-password
Polling Interval: 30
```

---

## 🛠️ Development

### Local Development Setup

```bash
# Clone and setup
git clone https://github.com/neerpatel/Hubitat.git
cd Hubitat/bridge-node

# Install dependencies
npm install

# Development mode (with auto-reload)
npm run dev

# TypeScript compilation
npm run build

# Type checking
npm run type-check
```

### Project Structure

```
Hubitat/
├── bridge-node/                    # Node.js TypeScript bridge server
│   ├── src/                       # TypeScript source files
│   ├── hubspace-server.js         # Main server file
│   ├── package.json               # Node.js dependencies
│   └── ecosystem.config.js        # PM2 configuration
├── Hubitat/                       # Hubitat Groovy files
│   └── Hubspace/                  # HubSpace device drivers
│       └── HubspaceDeviceManager.groovy
├── docs/                          # Documentation
│   └── Hubspace.md               # This file
└── setup.sh                      # Automated deployment script
```

---

## 🏠 Hubitat Integration

### Device Manager Features

- **Automatic Device Discovery** - Scans HubSpace account for available devices
- **Real-time State Updates** - Keeps Hubitat in sync with cloud device states
- **Secure Authentication** - OAuth flow handled securely through bridge
- **Dashboard Integration** - Full compatibility with Hubitat dashboards
- **Multi-Device Support** - Supports lights, fans, switches, and more

### Supported Device Types

| Device Type | Capabilities | Status | Notes |
|-------------|--------------|---------|-------|
| **Smart Lights** | On/Off, Dimming, Color | ✅ Supported | Full feature support |
| **Ceiling Fans** | Speed Control, Direction | ✅ Supported | Multiple speed levels |
| **Smart Switches** | On/Off Control | ✅ Supported | Basic switching |
| **Plugs/Outlets** | On/Off Control | ✅ Supported | Smart outlet control |
| **Motion Sensors** | Motion Detection | 🔄 In Progress | Read-only support |
| **Door/Window Sensors** | Open/Closed Status | 🔄 In Progress | Contact sensor support |

### Installation Steps

1. **Install the Device Manager**
   - In Hubitat admin, go to **Drivers Code**
   - Click **New Driver**
   - Copy/paste the contents of `Hubitat/Hubspace/HubspaceDeviceManager.groovy`
   - Click **Save**

2. **Create a Virtual Device**
   - Go to **Devices**
   - Click **Add Device**
   - Select **Virtual**
   - Choose **User driver** and select **HubSpace Device Manager**
   - Enter a name like "HubSpace Manager"
   - Click **Save**

3. **Configure the Device**
   - Open your new HubSpace Manager device
   - Click **Preferences**
   - Enter your bridge server URL and HubSpace credentials
   - Click **Save Preferences**

4. **Discover Devices**
   - Click **Refresh** or **Discover Devices**
   - Your HubSpace devices will be automatically imported

---

## 🔧 Troubleshooting

### Common Issues

#### Bridge Server Won't Start

**Problem**: Server fails to start with module errors
```
Error: Cannot find module 'express'
```

**Solution**: 
```bash
cd bridge-node
npm install
```

#### Authentication Failures

**Problem**: "Invalid credentials" error in Hubitat logs

**Solutions**:
1. Verify HubSpace credentials work in the mobile app
2. Check bridge server URL is accessible from Hubitat hub
3. Ensure bridge server is running (`curl http://your-server:3000/health`)

#### Devices Not Discovered

**Problem**: Device discovery returns empty list

**Solutions**:
1. Verify you have devices in your HubSpace mobile app
2. Check debug logging for authentication errors
3. Restart bridge server and try again

#### Connection Timeouts

**Problem**: Hubitat can't reach bridge server

**Solutions**:
1. Verify network connectivity between Hubitat and bridge server
2. Check firewall settings on bridge server host
3. Try accessing bridge health endpoint: `http://your-server:3000/health`

### Debug Logging

Enable debug logging in the Hubitat device preferences to see detailed communication logs:

```
[INFO] HubSpace: Authenticating with bridge server
[DEBUG] Bridge response: {"sessionId":"abc123","accountId":"456789"}
[INFO] HubSpace: Discovered 5 devices
[DEBUG] Device: Smart Light (ID: 12345) - Type: light
```

### Health Check

The bridge server provides a health endpoint to verify it's running correctly:

```bash
curl http://your-bridge-server:3000/health
```

Expected response:
```json
{
  "status": "ok",
  "uptime": 3600,
  "sessions": 1,
  "version": "0.2.0"
}
```

---

## 🤝 Contributing

🌟 **Help improve HubSpace integration!** 🌟

| Type | Description | How to Help |
|------|-------------|-------------|
| 🐛 **Bug Reports** | Found an issue? | [Open an issue](https://github.com/neerpatel/Hubitat/issues) |
| ✨ **Device Support** | New device type? | Test and submit device capabilities |
| 🔧 **Code Contributions** | Want to code? | Fork, develop, and submit a PR |
| 📖 **Documentation** | Improve docs | Help us make setup even easier |

### Development Guidelines

- Follow TypeScript best practices for bridge server
- Use Groovy conventions for Hubitat drivers  
- Test thoroughly with actual HubSpace devices
- Document any new features or device support
- Update device compatibility table when adding support

### Testing Checklist

Before submitting changes:
- [ ] Bridge server starts without errors
- [ ] Authentication flow works with valid credentials
- [ ] Device discovery returns expected devices
- [ ] Device commands work (on/off, dimming, etc.)
- [ ] State updates reflect in Hubitat correctly
- [ ] Error handling works for invalid inputs

---

## 📬 Support & Community

[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-red)](https://github.com/neerpatel/Hubitat/issues) [![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-blue)](https://github.com/neerpatel/Hubitat/discussions)

**Questions? Ideas? Need help?**  
We'd love to hear from you!

- 🐛 **Bug reports**: [GitHub Issues](https://github.com/neerpatel/Hubitat/issues)
- 💬 **General discussion**: [GitHub Discussions](https://github.com/neerpatel/Hubitat/discussions)
- 📖 **Documentation**: Check our [Wiki](https://github.com/neerpatel/Hubitat/wiki)
- 🔧 **HubSpace specific**: Tag issues with `hubspace` label

### 🌟 Star this repo if it helped you! 🌟

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

**What this means:**
- ✅ **Freedom to use** - Use this software for any purpose
- ✅ **Freedom to study** - Access and examine the source code
- ✅ **Freedom to modify** - Make changes and improvements
- ✅ **Freedom to distribute** - Share copies with others
- ⚠️ **Copyleft requirement** - Derivative works must also be AGPL-3.0
- ⚠️ **Network use provision** - If you run this software on a server, you must provide source code to users

For the complete license text, see the [LICENSE](../LICENSE) file in the repository root.

## Credit
Additionally, show these projects some love, as I was inspired by their work to make this project : 
- Inspired by: https://github.com/jdeath/Hubspace-Homeassistant and https://github.com/Expl0dingBanana/aioafero
- App structure and device management patterns adapted from: https://github.com/DaveGut/HubitatActive


---

**Made with ❤️ for Hubitat and Home Automation communities**
