#!/bin/bash
set -e

echo "Building and starting chat servers..."
docker compose up --build -d

echo ""
echo "Servers are running:"
echo "  TCP  → tcp://localhost:8083"
echo "  WS   → ws://localhost:8082"
echo "  REST → http://localhost:8081"
echo ""
echo "Run ./client.sh to connect."
