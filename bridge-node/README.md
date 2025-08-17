# HubSpace Bridge Server (TypeScript)

A TypeScript Express.js server that provides a bridge API for HubSpace devices, handling OAuth authentication and device management.

## Features

- **OAuth 2.0 with PKCE**: Secure authentication flow with Proof Key for Code Exchange
- **Session Management**: In-memory session store for managing authenticated users
- **Device Discovery**: Fetch and list all HubSpace devices for an account
- **Device Control**: Send commands to control HubSpace devices
- **State Monitoring**: Get real-time device state information
- **TypeScript**: Full type safety and better development experience

## API Endpoints

### Authentication
- `POST /login` - Authenticate with HubSpace credentials
  ```json
  {
    "username": "your-email@example.com",
    "password": "your-password"
  }
  ```
  Returns: `{ "sessionId": "uuid", "accountId": "account-id" }`

### Device Management
- `GET /devices?session=<sessionId>` - List all devices
- `GET /state/:deviceId?session=<sessionId>` - Get device state
- `POST /command/:deviceId?session=<sessionId>` - Send device command
  ```json
  {
    "values": [
      {
        "functionClass": "power",
        "functionInstance": "1", 
        "value": "on"
      }
    ]
  }
  ```

## Development Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Compile TypeScript to JavaScript
- `npm start` - Run compiled JavaScript server
- `npm run type-check` - Type check without emitting files
- `npm run clean` - Remove compiled files

## Project Structure

```
src/
├── server.ts          # Main Express server with TypeScript
├── types/             # Type definitions (future)
└── utils/             # Utility functions (future)

dist/                  # Compiled JavaScript output
├── server.js
├── server.d.ts
└── *.map             # Source maps
```

## Development

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start development server:
   ```bash
   npm run dev
   ```

3. The server will start on port 3000 (or PORT environment variable)

## Building for Production

1. Compile TypeScript:
   ```bash
   npm run build
   ```

2. Start production server:
   ```bash
   npm start
   ```

## Type Safety

This TypeScript version provides:

- **Interface Definitions**: Clear types for all request/response objects
- **Type-safe Express Routes**: Properly typed request/response parameters
- **Error Handling**: Consistent error types throughout the application
- **Development Experience**: IntelliSense, auto-completion, and compile-time error checking

## Configuration

Set environment variables:
- `PORT` - Server port (default: 3000)

## Authentication Flow

1. Generate PKCE challenge and verifier
2. GET OAuth authorization page to extract session parameters
3. POST credentials to authenticate
4. Extract authorization code from redirect
5. Exchange code for access and refresh tokens
6. Use tokens for API requests with automatic refresh

## Device Integration

The server interfaces with the HubSpace/Afero API:
- **Authentication**: `accounts.hubspaceconnect.com`
- **API**: `api2.afero.net`
- **Data**: `semantics2.afero.net`

Supports all HubSpace device types including lights, fans, switches, thermostats, locks, and security systems.
