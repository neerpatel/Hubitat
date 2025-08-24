# 🌉 Hubitat Bridge Server

### Cloud Device Integration Bridge for Hubitat

![Node.js](https://img.shields.io/badge/Node.js-18+-green) ![TypeScript](https://img.shields.io/badge/TypeScript-Ready-blue) ![Express](https://img.shields.io/badge/Express-Server-red) ![PM2](https://img.shields.io/badge/PM2-Production-orange) ![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue)

🚀 **A minimal, secure Express server that proxies cloud authentication and device control for Hubitat integrations.**

The bridge server implements OAuth flows and device management for cloud providers, starting with **HubSpace/Afero**. The service is intentionally generic and extensible, allowing additional providers to be added over time under the same bridge architecture.

---

## 📋 Quick Navigation

| Section | Description |
|---------|-------------|
| 🚀 [Quick Start](#-quick-start) | Get running in minutes |
| ⚙️ [Configuration](#-configuration) | Server setup and environment |
| 🌐 [API Reference](#-api-reference) | Complete endpoint documentation |
| 🔧 [Development](#-development) | Local development setup |
| 🏠 [Hubitat Integration](#-hubitat-integration) | Pairing with Hubitat hub |
| 🐛 [Troubleshooting](#-troubleshooting) | Common issues and solutions |

---

## 🚀 Quick Start

### ⚡ Get your bridge server running in minutes!

| Step | Action | Command |
|------|--------|---------|
| 1️⃣ | **Install Dependencies** | `cd bridge-node && npm install` |
| 2️⃣ | **Development Mode** | `npm start` |
| 3️⃣ | **Production Mode** | `pm2 start ecosystem.config.js` |
| 4️⃣ | **Verify Running** | `curl http://localhost:3000/health` |

### 🎉 Your bridge server is now ready for Hubitat integration!

---

## 📋 Requirements

| Requirement | Version | Purpose |
|-------------|---------|---------|
| **Node.js** | 18+ | ES Modules support |
| **npm** | Latest | Package management |
| **Network Access** | - | HubSpace/Afero API connectivity |

---

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `PORT` | Server listening port | `3000` | No |
| `NODE_ENV` | Environment mode | `development` | No |

### Production Deployment with PM2

```bash
# Install PM2 globally
npm install -g pm2

# Start bridge server
cd bridge-node
pm2 start ecosystem.config.js

# Save PM2 configuration
pm2 save

# Enable auto-start on boot (optional)
pm2 startup
```

---

## 🌐 API Reference

### 🔐 Authentication

#### POST `/login`
Authenticate with HubSpace cloud and create session.

**Request:**
```json
{
  "username": "your-email@example.com",
  "password": "your-password"
}
```

**Response:**
```json
{
  "sessionId": "uuid-session-id",
  "accountId": "account-identifier"
}
```

---

### 📱 Device Management

#### GET `/devices?session=<sessionId>`
Retrieve all devices for the authenticated account.

**Response:**
```json
[
  {
    "id": "metadevice-id",
    "deviceId": "physical-device-id", 
    "typeId": "metadevice.device",
    "device_class": "light",
    "friendlyName": "Living Room Light",
    "children": ["child-id-1", "child-id-2"],
    "states": { /* optional raw state */ }
  }
]
```

#### GET `/state/:deviceId?session=<sessionId>`
Get current state for a specific device.

**Response:**
```json
{
  "brightness": 75,
  "power": "on", 
  "color-temperature": 3500
}
```

---

### 🎛️ Device Control

#### POST `/command/:deviceId?session=<sessionId>`
Send commands to control devices.

**Light Control Examples:**
```json
// Turn on/off
{ "values": [{ "functionClass": "power", "value": "on" }] }

// Set brightness
{ "values": [{ "functionClass": "brightness", "value": 75 }] }

// Set color temperature
{ "values": [{ "functionClass": "color-temperature", "value": 3500 }] }
```

**Fan Control Examples:**
```json
// Set fan speed
{ 
  "values": [{ 
    "functionClass": "fan-speed", 
    "functionInstance": "fan-speed", 
    "value": "fan-speed-3-066" 
  }] 
}

// Set fan direction
{ 
  "values": [{ 
    "functionClass": "fan-reverse", 
    "functionInstance": "fan-reverse", 
    "value": "forward" 
  }] 
}
```

---

### 🏥 Health Check

#### GET `/health`
Server health and status information.

**Response:**
```json
{
  "status": "ok",
  "uptime": 3600,
  "sessions": 2,
  "version": "0.2.0"
}
```

---

## 🔧 Development

### Local Development Setup

```bash
# Clone and navigate
git clone https://github.com/neerpatel/Hubitat.git
cd Hubitat/bridge-node

# Install dependencies
npm install

# Development mode (auto-reload)
npm run dev

# Production build
npm run build

# Type checking
npm run type-check
```

### Testing the API

```bash
# Set base URL
BASE=http://localhost:3000

# Test authentication
curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"you@example.com","password":"secret"}'

# Use returned session ID
SESSION="your-session-id-here"

# List devices
curl -sS "$BASE/devices?session=$SESSION" | jq '.'

# Get device state
curl -sS "$BASE/state/device-id?session=$SESSION" | jq '.'

# Control device
curl -sS -X POST "$BASE/command/device-id?session=$SESSION" \
  -H 'Content-Type: application/json' \
  -d '{"values":[{"functionClass":"power","value":"on"}]}'
```

---

## 🏠 Hubitat Integration

### Pairing with Hubitat Hub

| Step | Action | Details |
|------|--------|---------|
| 1️⃣ | **Install Device Manager** | Import `HubspaceDeviceManager.groovy` into Hubitat |
| 2️⃣ | **Create Virtual Device** | Use HubSpace Device Manager driver |
| 3️⃣ | **Configure Bridge URL** | Set to `http://your-server:3000` |
| 4️⃣ | **Enter Credentials** | Provide HubSpace username/password |
| 5️⃣ | **Connect to Bridge** | Click "Connect to Bridge" button |
| 6️⃣ | **Discover Devices** | Run device discovery to import devices |

### Device Relationships

- **Parent Devices**: Multi-component devices (e.g., ceiling fan with light)
- **Child Devices**: Individual components automatically created by Hubitat
- **Standalone Devices**: Single-function devices (e.g., smart switch)

---

## 🐛 Troubleshooting

### Common Issues

#### 🔌 Server Won't Start

**Problem**: Module errors or port conflicts
```
Error: Cannot find module 'express'
Error: listen EADDRINUSE :::3000
```

**Solutions**:
```bash
# Install dependencies
npm install

# Use different port
PORT=3001 npm start

# Kill process on port 3000
lsof -ti:3000 | xargs kill
```

#### 🔐 Authentication Failures

**Problem**: Login returns 401 or invalid credentials

**Solutions**:
1. Verify credentials work in HubSpace mobile app
2. Check network connectivity to HubSpace servers
3. Review server logs: `pm2 logs hubspace-bridge`

#### 🏠 Hubitat Connection Issues

**Problem**: Hubitat can't reach bridge server

**Solutions**:
1. Verify bridge URL format: `http://ip:port` (not https)
2. Check firewall settings on bridge server host
3. Test connectivity: `curl http://bridge-ip:3000/health`

### Command Errors

#### 🚨 400 Bad Request

Common causes and solutions:

| Error | Cause | Solution |
|-------|-------|----------|
| Invalid `color-rgb` | Sending RGB to CT-only light | Check device capabilities first |
| Invalid `fan-speed` | Wrong speed value format | Use device's supported values |
| Missing session | Session expired or invalid | Re-authenticate with `/login` |

#### 📊 Debug Logging

Enable detailed logging by checking server console output:

```bash
# PM2 logs
pm2 logs hubspace-bridge

# Direct console (development)
npm start
```

---

## 🔄 Session Management

### Important Notes

- **In-Memory Storage**: Sessions stored in server memory
- **Server Restart**: Restarting clears all sessions
- **Auto-Refresh**: Access tokens automatically refreshed
- **Re-Authentication**: Click "Connect to Bridge" in Hubitat after server restart

### Session Lifecycle

1. **Login**: Creates session with refresh token
2. **API Calls**: Automatic token refresh before expiration
3. **Server Restart**: All sessions cleared, require re-login
4. **Token Expiry**: Handled automatically with refresh token

---

## 📚 Architecture Notes

### Design Principles

- **Minimal & Secure**: Lean codebase with OAuth best practices
- **Provider Agnostic**: Extensible for additional cloud services
- **Stateless API**: RESTful endpoints with session-based auth
- **Production Ready**: PM2 process management and error handling

### Future Extensibility

The bridge is designed to support additional cloud providers:

```javascript
// Future provider structure
/api/v1/provider/{hubspace|smartthings|other}/
```

---

## 🙏 Credits & Inspiration

| Source | Contribution |
|--------|--------------|
| [Hubspace-Homeassistant](https://github.com/jdeath/Hubspace-Homeassistant) | HubSpace API reverse engineering |
| [aioafero](https://github.com/Expl0dingBanana/aioafero) | Afero protocol implementation |
| [HubitatActive](https://github.com/DaveGut/HubitatActive) | Hubitat app patterns and device management |

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

The AGPL-3.0 is a copyleft license that ensures the software remains free and open source. If you run this software on a server and provide it as a service to others, you must also provide the source code.

See the [LICENSE](../LICENSE) file in the repository root for full license details.

---

**Made with ❤️ for the Hubitat community**

*Bridging cloud devices to your local smart home hub, securely and efficiently.*

## Requirements

- Node.js 18+ (ESM) and npm
- Network access to HubSpace (Afero) APIs from the host running the bridge

## Install

```bash
cd bridge-node
npm install
```

## Run

- Development:
  - `npm start` (runs `server.js` on `PORT` or 3000)

- With PM2 (recommended):
  ```bash
  cd bridge-node
  pm2 start ecosystem.config.js
  pm2 save
  pm2 startup   # optional: enable on-boot
  ```

## Configuration

- `PORT`: listening port (default: `3000`)

## API

- POST `/login`
  - Body: `{ "username": "email", "password": "password" }`
  - Response: `{ "sessionId": "uuid", "accountId": "..." }`

- GET `/devices?session=<sessionId>`
  - Returns minimal metadevice list for the account
  - Shape per item:
    - `id`: metadevice id
    - `deviceId`: physical device id (shared between parent/children)
    - `typeId`: resource type id (expect `metadevice.device`)
    - `device_class`: deviceClass (e.g., `light`, `fan`, `ceiling-fan`)
    - `friendlyName`: user label or default device name
    - `children`: array of child metadevice ids (if parent)
    - `states`: raw state (optional, when included by Afero)

- GET `/state/:deviceId?session=<sessionId>`
  - Returns raw state payload for a metadevice

- POST `/command/:deviceId?session=<sessionId>`
  - Body: `{ "values": [ { "functionClass": "power", "functionInstance": "fan-power", "value": "on" } ] }`
  - For lights, examples: `{ functionClass: "brightness", value: 75 }`, `{ functionClass: "color-temperature", value: 3500 }`
  - For fans, examples: `{ functionClass: "fan-speed", functionInstance: "fan-speed", value: "fan-speed-3-066" }`, `{ functionClass: "fan-reverse", functionInstance: "fan-reverse", value: "forward" }`

## Quick test

```bash
BASE=http://localhost:3000
curl -sS -X POST "$BASE/login" -H 'content-type: application/json' \
  -d '{"username":"you@example.com","password":"secret"}'
# => {"sessionId":"...","accountId":"..."}

SESSION=... # paste from above
curl -sS "$BASE/devices?session=$SESSION" | jq '.'
```

## Hubitat app pairing

- In the Hubitat app (HubSpace Device Manager):
  - Set Bridge URL to the PM2/Node host (e.g., `http://<host>:3000`)
  - Enter your HubSpace credentials and click “Connect to Bridge”
  - Discover devices (app lists only parents/standalones)
  - Adding a parent creates Hubitat children for each child metadevice

## Notes

- Sessions are stored in-memory in the Node process; restarting the bridge clears sessions. Re-run `/login` from Hubitat to reconnect.
- The bridge auto-refreshes access tokens when expired.
- Device responses include `deviceId` and `children` which the app uses to build parent/child relationships.

## Credit

- Inspired by: https://github.com/jdeath/Hubspace-Homeassistant and https://github.com/Expl0dingBanana/aioafero
- App structure and device management patterns adapted from: https://github.com/DaveGut/HubitatActive

## Troubleshooting

- Check PM2 logs: `pm2 logs hubspace-bridge`
- Common 400s:
  - Sending `color-rgb` to CT-only lights → ensure you only send color when the device supports it.
  - Invalid fan-speed value → must be one of the device’s supported category values (e.g., `fan-speed-3-033`).

## 📄 License

AGPL-3.0 License - see repository root for full license details.

This software is free and open source under the GNU Affero General Public License v3.0.
