# UPS API - Raspberry Pi UPS Monitoring System

## System Architecture

This is a Raspberry Pi-based UPS monitoring system that bridges APC UPS devices with Hubitat home automation hubs. The system has three main components:

- **Express API Server** (`app/server.js`) - REST endpoints for UPS data
- **Cron Service** (`app/cron.js`) - Periodic UPS status polling
- **APC UPS Daemon** (apcupsd) - System service for UPS communication

## Data Flow

1. `apcupsd` daemon communicates with APC UPS via USB/network
2. API server queries UPS data via `apcaccess` command or network socket (port 3551)
3. Cron service polls `/hubitat?event=cron` every 5 minutes
4. Hubitat endpoint (`/hubitat`) fetches UPS data and forwards to Hubitat hub
5. Hubitat device driver (`Hubitat/UPSMonitor.groovy`) processes notifications

## Key Endpoints

- **GET /ups/status** - Network socket query to apcupsd (port 3551)
- **GET /ups/apcaccess** - Shell command `apcaccess` execution
- **GET /hubitat** - Combines UPS data with event info, posts to Hubitat hub

## Development Patterns

### Environment Configuration

All configuration is in `app/ecosystem.config.js`:

```javascript
HUB_IP: "192.168.107.116"; // Hubitat hub IP
HUB_PORT: 39501; // Hubitat maker API port
APCNIS_IP: "127.0.0.1"; // apcupsd network interface
APCNIS_PORT: 3551; // apcupsd network port
CRON: "*/5 * * * *"; // 5-minute polling interval
```

### Logging

Uses Winston logger (`app/config/winston.js`) with file rotation. All API calls log IP addresses and request details.

### Error Handling

Standard pattern: log errors, return 500 status with error object in JSON.

## Deployment Workflow

The system uses PM2 for process management with remote deployment:

```bash
# Initial setup (from README.md)
curl -sL https://raw.githubusercontent.com/neerpatel/upsapi/main/setup.sh | sudo -E bash -
curl -sL https://raw.githubusercontent.com/neerpatel/upsapi/main/app/ecosystem.config.js -O
pm2 deploy production setup && pm2 deploy production
```

### Deployment Process

1. `setup.sh` - Installs Node.js, pm2, apcupsd, creates `/opt/upsapi`
2. `pm2 deploy production` - Clones repo, runs `post-deploy.sh`
3. `post-deploy.sh` - Configures apcupsd from template, installs npm dependencies
4. PM2 runs two processes: `upsapi` (server) and `cron` (scheduler)

### APC Configuration

The `apcupsd-scripts/apcupsd.conf.tpl` template uses environment variable substitution (`__VARIABLE__` → `${VARIABLE}`). The `post-deploy.sh` script processes this template and copies it to `/etc/apcupsd/`.

## Integration Points

### Hubitat Integration

- Posts to `http://{HUB_IP}:{HUB_PORT}/notify` with combined UPS data
- Uses Referer header "apcupsd" for identification
- Groovy device driver expects specific JSON structure with UPS metrics

### UPS Communication

- Primary: Network socket to apcupsd daemon (more reliable)
- Fallback: Shell command `apcaccess` (for compatibility)
- Both return same key-value format (parsed from status output)

## Common Modifications

When adding new UPS metrics, update both:

1. API parsing logic in `app/api/ups.js` (both `query()` and `apcaccess()` functions)
2. Hubitat device driver capabilities in `Hubitat/UPSMonitor.groovy`

Environment changes require updating `ecosystem.config.js` and redeploying via PM2.
