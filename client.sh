#!/bin/bash
set -e

IMAGE="networkchat-client"

echo "Building client image..."
docker build -t "$IMAGE" . -q

echo ""
echo "Choose protocol:"
echo "  1) TCP       — port 8083  | commands: /join ROOM, /exit"
echo "  2) WebSocket — port 8082  | commands: /nick NAME, /exit"
echo "  3) REST+SSE  — port 8081  | commands: /nick NAME, /room, /exit"
echo ""
read -r -p "Enter number [1-3]: " choice

case "$choice" in
  1) MAIN_CLASS="org.dave.networkchat.tcp.TcpClient" ;;
  2) MAIN_CLASS="org.dave.networkchat.ws.WsClient" ;;
  3) MAIN_CLASS="org.dave.networkchat.rest.RestClient" ;;
  *) echo "Invalid choice: $choice"; exit 1 ;;
esac

docker run -it --rm \
  --add-host=host.docker.internal:host-gateway \
  -e SERVER_HOST=host.docker.internal \
  "$IMAGE" "$MAIN_CLASS"
