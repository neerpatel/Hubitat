# 🏠 Hubitat Development Hub 🔧

### Custom Drivers, Apps, and Integrations for Hubitat

![Hubitat](https://img.shields.io/badge/Hubitat-Compatible-orange) ![Groovy](https://img.shields.io/badge/Groovy-Driver-blue) ![TypeScript](https://img.shields.io/badge/TypeScript-Bridge-green) ![License: MIT](https://img.shields.io/badge/License-MIT-yellow)

![GitHub Stars](https://img.shields.io/github/stars/neerpatel/Hubitat) ![GitHub Issues](https://img.shields.io/github/issues/neerpatel/Hubitat) ![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen)

🌟 **A comprehensive collection of Hubitat drivers, apps, and bridge integrations!**  
Extend your Hubitat hub's capabilities with custom drivers for various smart home devices and cloud service integrations.

---

## 📋 Projects & Integrations

| Project | Description | Status | Documentation |
|---------|-------------|--------|---------------|
| 🏠 **HubSpace Integration** | Bridge for HubSpace/Afero cloud devices | ✅ Production | [docs/Hubspace.md](docs/Hubspace.md) |
| 🔌 **Generic Device Drivers** | Collection of custom device drivers | 🚧 Development | Coming Soon |
| 🤖 **Automation Apps** | Smart home automation applications | 📅 Planned | Coming Soon |
| 🌐 **Cloud Bridges** | Additional cloud service integrations | 💭 Ideas | Coming Soon |

---

## 🚀 Quick Start

### 🎯 Choose your integration:

#### HubSpace Integration
Connect HubSpace/Afero cloud devices to your Hubitat hub through a secure bridge server.

```bash
# Clone repository
git clone https://github.com/neerpatel/Hubitat.git

# Setup HubSpace bridge
cd Hubitat/bridge-node
npm install
npm start
```

**📖 Full Setup Guide**: [docs/Hubspace.md](docs/Hubspace.md)

#### Generic Device Drivers
Install custom drivers for specific device types directly in Hubitat.

1. Navigate to **Drivers Code** in Hubitat admin
2. Click **New Driver** 
3. Copy/paste driver code from `Hubitat/drivers/`
4. Save and create virtual device

---

## 🏗️ Repository Structure

```
Hubitat/
├── 📁 bridge-node/              # Node.js bridge servers
│   ├── hubspace-server.js       # HubSpace OAuth bridge
│   ├── package.json             # Node.js dependencies
│   └── ecosystem.config.js      # PM2 configuration
├── 📁 Hubitat/                 # Hubitat Groovy files
│   ├── 📁 drivers/              # Device drivers
│   ├── 📁 apps/                 # Automation apps  
│   └── 📁 Hubspace/             # HubSpace integration
├── 📁 docs/                    # Documentation
│   └── Hubspace.md             # HubSpace setup guide
├── 📁 .github/                 # GitHub workflows & templates
└── 📄 README.md                # This file
```

---

## ✨ Featured Integrations

### 🏠 HubSpace Bridge
**Status**: ✅ Production Ready

Connect your Hubitat hub to HubSpace cloud devices including:
- Smart lights with dimming and color control
- Ceiling fans with speed and direction control  
- Smart switches and outlets
- Motion and contact sensors (coming soon)

**Key Features**:
- 🔐 Secure OAuth proxy with PKCE authentication
- ⚡ Real-time device state synchronization
- 🚀 Easy deployment with PM2 process management
- 📊 Full Hubitat dashboard compatibility

**Get Started**: [docs/Hubspace.md](docs/Hubspace.md)

### 🔌 Device Drivers Collection
**Status**: 🚧 In Development

Custom Groovy drivers for various smart home devices:
- Generic Z-Wave/Zigbee device support
- Custom protocol implementations
- Enhanced capabilities for existing devices
- Community-contributed drivers

### 🤖 Automation Apps
**Status**: 📅 Planned

Smart home automation applications:
- Advanced scheduling and triggers
- Multi-device coordination
- Custom notification systems  
- Integration helpers

---

## �️ Development

### Contributing to Existing Projects

Each project has its own development guidelines:

| Project | Language | Setup | Testing |
|---------|----------|-------|---------|
| **HubSpace Bridge** | TypeScript/Node.js | `npm install` | `npm test` |
| **Groovy Drivers** | Groovy | Import to Hubitat | Test with devices |
| **Apps** | Groovy | Import to Hubitat | Integration testing |

### Adding New Projects

1. **Create project directory** under appropriate folder
2. **Add documentation** in `docs/ProjectName.md`
3. **Update this README** with project entry
4. **Follow coding standards** for the platform
5. **Include setup instructions** and examples

### Development Guidelines

- **Groovy Code**: Follow Hubitat conventions and best practices
- **TypeScript/Node.js**: Use modern ES features and proper typing
- **Documentation**: Include comprehensive setup and usage guides
- **Testing**: Test thoroughly with actual devices when possible
- **Version Control**: Use meaningful commit messages and branch names

---

## 📚 Documentation

| Resource | Description |
|----------|-------------|
| [docs/Hubspace.md](docs/Hubspace.md) | Complete HubSpace integration guide |
| [.github/instructions/](https://github.com/neerpatel/Hubitat/tree/main/.github/instructions) | Development guidelines and standards |
| [Hubitat Documentation](https://docs2.hubitat.com/) | Official Hubitat developer docs |
| [GitHub Wiki](https://github.com/neerpatel/Hubitat/wiki) | Community guides and examples |

---

## 🤝 Contributing

🌟 **We welcome contributions to all projects!** 🌟

| Type | Description | How to Help |
|------|-------------|-------------|
| 🐛 **Bug Reports** | Found an issue? | [Open an issue](https://github.com/neerpatel/Hubitat/issues) |
| ✨ **New Features** | Have an idea? | [Request a feature](https://github.com/neerpatel/Hubitat/issues) |
| 🔧 **Code Contributions** | Want to code? | Fork, develop, and submit a PR |
| 📖 **Documentation** | Improve guides | Help make setup even easier |
| 🧪 **Device Testing** | Have compatible devices? | Test and report compatibility |

### Project-Specific Contributing

- **HubSpace**: Test with HubSpace devices, improve OAuth flow
- **Drivers**: Submit drivers for new device types
- **Apps**: Create automation apps for common use cases
- **Documentation**: Improve setup guides and troubleshooting

---

## 💡 Why Choose This Repository?

| Benefit | Description |
|---------|-------------|
| 🏠 **Local Control Focus** | Keep automation local while integrating cloud services |
| � **Security First** | Secure authentication patterns and best practices |
| ⚡ **Performance Optimized** | Efficient code with minimal resource usage |
| 🔧 **Extensible Architecture** | Easy to modify and extend for new use cases |
| 🌟 **Community Driven** | Open source with active development |
| 📖 **Well Documented** | Comprehensive guides and examples |

---

## 📬 Support & Community

[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-red)](https://github.com/neerpatel/Hubitat/issues) [![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-blue)](https://github.com/neerpatel/Hubitat/discussions)

**Questions? Ideas? Need help?**  
We'd love to hear from you!

- 🐛 **Bug reports**: [GitHub Issues](https://github.com/neerpatel/Hubitat/issues)
- 💬 **General discussion**: [GitHub Discussions](https://github.com/neerpatel/Hubitat/discussions)
- 📖 **Documentation**: Check our [Wiki](https://github.com/neerpatel/Hubitat/wiki)
- 🔧 **Project-specific**: Tag issues with project name (e.g., `hubspace`, `drivers`)

### 🌟 Star this repo if it helped you! 🌟

---

**Made with ❤️ for the Hubitat and Home Automation community**

*Empowering local smart home control with seamless cloud integrations.*

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

### 🎯 Get your bridge running in minutes!

| Step | Action | Command/Description |
|------|--------|-------------------|
| 1️⃣ | **Clone Repository** | `git clone https://github.com/neerpatel/Hubitat.git` |
| 2️⃣ | **Install Dependencies** | `cd Hubitat/bridge-node && npm install` |
| 3️⃣ | **Start Bridge Server** | `npm start` (dev) or `pm2 start ecosystem.config.js` (prod) |
| 4️⃣ | **Install Hubitat Driver** | Import `Hubitat/Hubspace/HubspaceDeviceManager.groovy` |
| 5️⃣ | **Configure Bridge URL** | Set bridge URL in device manager preferences |

### 🎉 That's it! Your HubSpace devices are now accessible in Hubitat!

---

## ⚙️ Configuration

### Bridge Server Configuration

| Environment Variable | Description | Default |
|----------------------|-------------|---------|
| `PORT` | Server listening port | `3000` |
| `NODE_ENV` | Environment mode | `development` |

### Hubitat Device Manager Settings

| Setting | Description |
|---------|-------------|
| **HubSpace Username** | Your HubSpace account email |
| **HubSpace Password** | Your HubSpace account password |
| **Bridge Server URL** | URL of your running bridge server |
| **Polling Interval** | Device state refresh frequency |

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
├── bridge-node/          # Node.js TypeScript bridge server
│   ├── src/             # TypeScript source files
│   ├── package.json     # Node.js dependencies
│   └── ecosystem.config.js  # PM2 configuration
├── Hubitat/             # Hubitat Groovy files
│   └── Hubspace/        # HubSpace device drivers
│       └── HubspaceDeviceManager.groovy
└── setup.sh            # Automated deployment script
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

| Device Type | Capabilities | Status |
|-------------|--------------|---------|
| **Smart Lights** | On/Off, Dimming, Color | ✅ Supported |
| **Ceiling Fans** | Speed Control, Direction | ✅ Supported |
| **Smart Switches** | On/Off Control | ✅ Supported |
| **Plugs/Outlets** | On/Off Control | ✅ Supported |

---

## 🤝 Contributing

🌟 **We welcome your contributions!** 🌟

| Type | Description | How to Help |
|------|-------------|-------------|
| 🐛 **Bug Reports** | Found an issue? | [Open an issue](https://github.com/neerpatel/Hubitat/issues) |
| ✨ **Feature Requests** | Have an idea? | [Request a feature](https://github.com/neerpatel/Hubitat/issues) |
| 🔧 **Code Contributions** | Want to code? | Fork, develop, and submit a PR |
| 📖 **Documentation** | Improve docs | Help us make setup even easier |

### Development Guidelines

- Follow TypeScript best practices for bridge server
- Use Groovy conventions for Hubitat drivers
- Test thoroughly with actual HubSpace devices
- Document any new features or device support

---

## 💡 Why Choose Hubitat HubSpace Bridge?

| Benefit | Description |
|---------|-------------|
| 🏠 **Local Control** | Keep your home automation local while accessing cloud devices |
| 🔐 **Security First** | OAuth proxy ensures your credentials stay secure |
| ⚡ **Performance** | Efficient bridge with minimal latency |
| 🔧 **Flexibility** | Extensible architecture for future device types |
| 🌟 **Community** | Open source with active development |
| 🚀 **Reliable** | Production-ready with PM2 process management |

---

## 📬 Support & Community

[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-red)](https://github.com/neerpatel/Hubitat/issues) [![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-blue)](https://github.com/neerpatel/Hubitat/discussions)

**Questions? Ideas? Need help?**  
We'd love to hear from you!

- 🐛 **Bug reports**: [GitHub Issues](https://github.com/neerpatel/Hubitat/issues)
- 💬 **General discussion**: [GitHub Discussions](https://github.com/neerpatel/Hubitat/discussions)
- 📖 **Documentation**: Check our [Wiki](https://github.com/neerpatel/Hubitat/wiki)

### 🌟 Star this repo if it helped you! 🌟

---

**Made with ❤️ for the Hubitat and Home Automation community**

*Bringing cloud devices to your local smart home hub, securely and efficiently.*
