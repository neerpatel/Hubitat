if [ ! -f .env ]; then
  export $(cat .env | xargs)
fi

echo "Running npm install"
cd ./app && npm install
cd ..