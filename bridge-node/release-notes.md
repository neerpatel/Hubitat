# Release Notes

## Version 0.2.2 (2025-08-24)

### 🐛 Bug Fixes

- **Fixed Circular JSON Logging Error**: Resolved "Converting circular structure to JSON" error in request logging
  - Removed circular references from logged request objects
  - Now logs only safe request properties (method, url, ip, userAgent, body, timestamp)
  - Improved error handling in logging middleware

- **Fixed node-fetch Import Error**: Resolved "fetch is not a function" error
  - Downgraded node-fetch from v3.x to v2.7.0 for proper CommonJS support
  - Removed dependency on ES modules for better compatibility
  - Ensured reliable HTTP client functionality

### 📋 Technical Notes

- **Compatibility**: All modules now use CommonJS for maximum compatibility
- **Logging**: Request logging now avoids circular references and only logs essential data
- **Dependencies**: Updated to use stable, CommonJS-compatible versions

---

## Version 0.2.1 (2025-08-24)

### � Bug Fixes

- **Fixed Circular JSON Logging Error**: Resolved "Converting circular structure to JSON" error in request logging
  - Removed circular references from logged request objects
  - Now logs only safe request properties (method, url, ip, userAgent, body, timestamp)
  - Improved error handling in logging middleware

- **Fixed node-fetch Import Error**: Resolved "fetch is not a function" error
  - Downgraded node-fetch from v3.x to v2.7.0 for proper CommonJS support
  - Removed dependency on ES modules for better compatibility
  - Ensured reliable HTTP client functionality

### �🔍 Enhanced Logging & Diagnostics

- **Winston Logger Integration**: Added comprehensive logging system with file and console output
  - Structured logging with timestamps for all major operations
  - Automatic log rotation (5MB max, 5 files retained)
  - Configurable log levels for debugging and production monitoring
  - Dedicated log file at `./logs/app.log` for persistent logging

- **Improved Debugging Capabilities**: Enhanced traceability across all bridge operations
  - Authentication flow logging with session tracking
  - Device discovery and state retrieval logging
  - Command execution logging with success/failure tracking
  - Session cleanup and management logging
  - Error logging with detailed context

- **Production Monitoring Support**: Better observability for production deployments
  - Application version reporting in logs
  - Session cleanup interval reporting
  - Graceful shutdown logging with SIGINT handling
  - Request/response logging for API calls

### 🔧 Dependencies & Infrastructure

- **New Dependencies**:
  - `winston`: ^3.17.0 - Professional logging framework
  - `app-root-path`: ^3.1.0 - Reliable application root path resolution

### 📋 Logging Details

- **Log Format**: `YYYY-MM-DD HH:mm:ss.SSS level: message`
- **Log Levels**: debug, info, warn, error
- **Log Rotation**: Automatic rotation at 5MB with 5 file retention
- **Console Output**: Colorized console logging for development
- **File Output**: Structured file logging for production monitoring

### 🚀 Operational Improvements

- **Session Management**: Enhanced session cleanup logging for monitoring expired sessions
- **Error Tracking**: Comprehensive error logging with context for debugging
- **Performance Monitoring**: Request timing and status logging for performance analysis
- **Authentication Tracking**: Login success/failure logging with user context (passwords masked)

### 📝 Notes

- Log files are created automatically in `./logs/` directory
- All sensitive information (passwords, tokens) are properly masked in logs
- Logging configuration can be adjusted via the winston.js configuration file
- Compatible with log aggregation systems for centralized monitoring

---

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

### 🚀 Optimizations & Improvements

- **CommonJS Module System**: No longer requires ES module configuration (`"type": "module"`)
  - Converted from ES modules to CommonJS for better compatibility
  - Simplified package.json configuration
  - Native file path handling without ES module workarounds
- **Improved Code Organization**: Logical separation of utility functions, authentication, and API endpoints
  - Centralized configuration constants in CONFIG object
  - Better JSDoc documentation throughout codebase
  - Organized functions into logical sections
- **Enhanced Session Management**: Automatic cleanup of expired sessions with configurable intervals
  - Added lastAccess tracking to sessions
  - Implemented automatic session expiration (1 hour of inactivity)
  - Configurable cleanup interval (60 seconds by default)
- **Better Error Handling**: More descriptive error messages and improved logging
- **Performance Improvements**: Optimized token refresh logic with configurable buffer times
- **Structured Configuration**: Centralized configuration constants for easier maintenance

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

- **Runtime**: Node.js with CommonJS modules support
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
- **node-fetch**: ^2.7.0 - HTTP client for API calls (downgraded from v3 for CommonJS compatibility)
- **cheerio**: ^1.0.0-rc.12 - HTML parsing for OAuth flow
- **qs**: ^6.12.1 - Query string parsing
- **uuid**: ^9.0.1 - Session ID generation

### 🛠️ Development Tools

- **nodemon**: ^3.1.0 - Development auto-reload
- **CommonJS Modules**: Standard Node.js module system
- **JSON Logging**: Structured logging for debugging and monitoring

### 📝 Notes

- Bridge server acts as secure proxy between Hubitat and HubSpace cloud
- No persistent storage required - all sessions maintained in memory
- Automatic reconnection and error recovery built-in
- Compatible with PM2 process management for production deployment

