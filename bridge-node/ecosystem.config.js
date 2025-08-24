/**
 * PM2 ecosystem config for the HubSpace Node bridge.
 *
 * Usage:
 *   cd bridge-node
 *   pm2 start ecosystem.config.js
 *   pm2 save
 *   pm2 startup   # optional: generate startup script for your OS
 */

module.exports = {
  apps: [
    {
      name: "hubitat-bridge",
      cwd: __dirname,
      script: "hubspace-server.js",
      exec_mode: "fork",
      instances: 1,
      watch: false,
      autorestart: true,
      max_memory_restart: "256M",
      node_args: "--enable-source-maps",
      env: {
        NODE_ENV: "production",
        // Change to override default port (3000)
        PORT: "3000",
      },
      env_development: {
        NODE_ENV: "development",
        PORT: "3000",
      },
      // Use PM2 default log locations (~/.pm2/logs). Uncomment below to log locally.
      // out_file: 'logs/pm2-out.log',
      // error_file: 'logs/pm2-err.log',
      // time: true,
    },
  ],
  deploy: {
    production: {
      user: "pi",
      host: ["127.0.0.1"],
      ref: "origin/main",
      repo: "https://github.com/neerpatel/Hubitat.git",
      path: "/opt/Hubitat",
      "pre-deploy-local": "",
      "post-setup": "ls -la",
      "post-deploy": "pwd && sh ./post-deploy.sh",
    },
  },
};
