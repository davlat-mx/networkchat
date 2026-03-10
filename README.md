# Network Chat

A multi-protocol chat server written in Java 17. Demonstrates the same chat room logic exposed over three different transports: raw TCP, WebSocket, and HTTP with Server-Sent Events (REST+SSE). All protocols share a single in-memory `ChatService`.

## Architecture

```
┌─────────────────────────────────────┐
│   ChatService  (rooms + broadcast)  │
└────────────┬──────────┬─────────────┘
             │          │          │
        TcpSession  WsSession  RestSession
             │          │          │
         TcpServer  WsServer   RestServer
          :8083      :8082       :8081
```

| Protocol | Port | Transport details |
|----------|------|-------------------|
| TCP      | 8083 | Raw sockets, one thread per client |
| WebSocket| 8082 | Full-duplex, room & name via URL query params |
| REST+SSE | 8081 | `GET /events` (SSE stream) + `POST /send` |

## Quick Start

> **Requirements:** Docker + Docker Compose.

### 1 — Start all servers

```bash
./server.sh
```

This builds the image once and starts three containers in the background.

### 2 — Connect a client

Open a new terminal and run:

```bash
./client.sh
```

Pick a protocol (1 / 2 / 3) and start chatting. Open additional terminals and run `./client.sh` again to have multiple users in the same room.

### Stop servers

```bash
docker compose down
```

---

## In-chat commands

| Protocol | Commands |
|----------|----------|
| TCP | `/join ROOM` — switch room · `/exit` — disconnect |
| WebSocket | `/nick NAME` — change username · `/exit` — disconnect |
| REST+SSE | `/nick NAME` — change username · `/room` — show current room · `/exit` — disconnect |

WebSocket clients set their name and room when connecting (prompted by `client.sh`).
TCP clients are prompted for name and room interactively after connecting.

---

## Running without Docker (local development)

Requires Java 17 and Maven 3.

```bash
# Build
mvn clean package

# Start a server (pick one)
mvn exec:java -Dexec.mainClass="org.dave.networkchat.tcp.TcpServer"
mvn exec:java -Dexec.mainClass="org.dave.networkchat.ws.WsServer"
mvn exec:java -Dexec.mainClass="org.dave.networkchat.rest.RestServer"

# Start a client (pick one)
mvn exec:java -Dexec.mainClass="org.dave.networkchat.tcp.TcpClient"
mvn exec:java -Dexec.mainClass="org.dave.networkchat.ws.WsClient"
mvn exec:java -Dexec.mainClass="org.dave.networkchat.rest.RestClient"
```

---

## Project structure

```
networkchat/
├── Dockerfile              # Multi-stage build → fat jar
├── docker-compose.yml      # Three server services
├── docker-entrypoint.sh    # Selects main class from env / arg
├── server.sh               # Build + start all servers in Docker
├── client.sh               # Interactive client launcher in Docker
├── pom.xml
└── src/main/java/org/dave/networkchat/
    ├── core/
    │   ├── ChatService.java     # Room management & broadcast
    │   └── ClientSession.java   # Protocol-agnostic session interface
    ├── tcp/    TcpServer · TcpSession · TcpClient
    ├── ws/     WsServer  · WsSession  · WsClient
    └── rest/   RestServer · RestSession · RestClient
```
