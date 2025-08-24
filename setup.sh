#!/bin/bash


# Check if Node.js is installed
if node -v > /dev/null 2>&1; then
    echo "Node.js is already installed"
else
    echo "Node.js is not installed, installing now..."
    sudo apt install -y ca-certificates curl gnupg git nodejs npm
    node -v
    npm -v
    echo "Node.js has been installed"
    #install pm2
    sudo npm install pm2@latest -g
    echo "pm2 has been installed"
fi    

if [ ! -d "/opt/Hubitat" ]; then
  echo "/opt/Hubitat does not exist."
  sudo mkdir /opt/Hubitat
  sudo chown pi:pi /opt/Hubitat -R
fi
if [ "$(stat -c '%U:%G' /opt/Hubitat)" != "pi:pi" ]; then
    echo "Updating folder ownership for /opt/Hubitat and sub"
    sudo chown -R pi:pi /opt/Hubitat
fi

curl -sL https://raw.githubusercontent.com/neerpatel/Hubitat/main/bridge-node/ecosystem.config.js -O
pm2 deploy production setup

cd /opt/Hubitat/source/bridge-node/
pm2 deploy production