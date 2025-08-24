# Release Notes

## Version 0.2.0 (2025-08-24)

### 🎉 Initial Bridge Server Release

- **OAuth Proxy Server**: Complete Node.js Express server for HubSpace cloud integration
  - PKCE-enabled authentication flow with automatic token management
  - Secure session handling with in-memory storage
  - Automatic token refresh and expiration management
  - No credential storage - authentication handled per session

- **RESTful API Endpoints**: Clean API interface for Hubitat integration
  - `POST /login` - Authenticate with HubSpace credentials
  - `GET /devices` - Discover and list available devices
  - `GET /state/:id` - Get real-time device state
  - `POST /command/:id` - Send device commands to HubSpace cloud
  - `GET /health` - Health check and server status

### 🔧 Core Features

- **Session Management**: UUID-based session tracking with automatic cleanup
- **Device Discovery**: Complete device enumeration with metadata normalization
- **State Synchronization**: Real-time device state retrieval from HubSpace API
- **Command Processing**: Bidirectional command translation between Hubitat and HubSpace
- **Error Handling**: Comprehensive error logging and graceful failure handling

### 🛡️ Security Features

- **Password Protection**: Passwords are masked in all logging output
- **Token Security**: Access tokens handled securely with automatic refresh
- **Request Logging**: Complete request/response logging for debugging (credentials excluded)
- **CORS Support**: Configurable cross-origin resource sharing

### 📋 Technical Specifications

- **Runtime**: Node.js with ES modules support
- **Framework**: Express.js web server
- **Authentication**: OAuth 2.0 with PKCE
- **API Version**: Afero API v1 integration
- **Session Storage**: In-memory with UUID tracking
- **Default Port**: 3000 (configurable via PORT environment variable)

### 🚀 Deployment Features

- **Process Management**: PM2 ecosystem configuration included
- **Development Mode**: Nodemon support for auto-reload during development
- **Production Ready**: Optimized for production deployment
- **Health Monitoring**: Built-in health endpoint for monitoring systems

### 📝 API Integration Details

- **HubSpace Cloud**: Full integration with accounts.hubspaceconnect.com
- **Device Classes**: Support for all HubSpace device types and function classes
- **State Management**: Real-time state synchronization with 5-second token refresh buffer
- **Command Translation**: Hubitat command format to HubSpace function class mapping

### 🔧 Dependencies

- **express**: ^4.19.2 - Web server framework
- **node-fetch**: ^3.3.2 - HTTP client for API calls
- **cheerio**: ^1.0.0-rc.12 - HTML parsing for OAuth flow
- **qs**: ^6.12.1 - Query string parsing
- **uuid**: ^9.0.1 - Session ID generation

### 🛠️ Development Tools

- **nodemon**: ^3.1.0 - Development auto-reload
- **ES Modules**: Modern JavaScript module system
- **JSON Logging**: Structured logging for debugging and monitoring

### 📝 Notes

- Bridge server acts as secure proxy between Hubitat and HubSpace cloud
- No persistent storage required - all sessions maintained in memory
- Automatic reconnection and error recovery built-in
- Compatible with PM2 process management for production deployment

---