# Network Chat

A multi-protocol chat server written in Java 17. Demonstrates the same chat room logic exposed over three different transports: raw TCP, WebSocket, and HTTP with Server-Sent Events (REST+SSE). All protocols share a single in-memory `ChatService` with shared chat history.

## Architecture

```
┌─────────────────────────────────────────────┐
│   ChatService  (rooms + broadcast + history) │
└────────────┬──────────┬─────────────────────┘
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

All commands are available across every protocol:

| Command | Description |
|---------|-------------|
| `/join ROOM` | Switch to another room (or create it) |
| `/rooms` | List all active rooms with user counts |
| `/nick NAME` | Change your display name |
| `/room` | Show the name of your current room |
| `/help` | Show all available commands |
| `/exit` | Disconnect from chat |

TCP clients are prompted for name and room interactively after connecting.
WebSocket and REST clients set their name and room when connecting (prompted by `client.sh`).

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
    │   ├── model/
    │   │   ├── ChatMessage.java       # Message entity (Lombok)
    │   │   └── ChatMessageType.java   # CHAT / SYSTEM enum
    │   ├── service/
    │   │   ├── ChatService.java       # Room management, broadcast, history, /rooms, /help
    │   │   ├── ChatServiceFactory.java# Shared singleton factory
    │   │   └── ClientSession.java     # Protocol-agnostic session interface
    │   ├── ChatHistory.java           # History store interface
    │   └── ChatHistoryImpl.java       # In-memory ConcurrentHashMap implementation
    ├── tcp/    TcpServer · TcpSession · TcpClient
    ├── ws/     WsServer  · WsChatServer · WsSession · WsClient
    └── rest/   RestServer · RestSession · RestClient
```
