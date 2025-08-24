if [ ! -f .env ]; then
  export $(cat .env | xargs)
fi

echo "Running npm install"
cd ./bridge-node && npm install
cd ..