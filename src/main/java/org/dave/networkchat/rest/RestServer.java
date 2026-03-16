package org.dave.networkchat.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dave.networkchat.core.service.ChatService;
import org.dave.networkchat.core.service.ChatServiceFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class RestServer {

    private static final int PORT = 8081;
    private static final String DEFAULT_NAME = "RestUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "/nick NAME, /join ROOM, /rooms, /room, /help, /exit";

    private final ChatService chatService;
    private final Map<String, RestSession> sessionsByClientId = new ConcurrentHashMap<>();

    public RestServer(ChatService chatService) {
        this.chatService = chatService;
    }

    public static void main(String[] args) throws Exception {
        ChatService chatService = ChatServiceFactory.createChatService();
        RestServer restServer = new RestServer(chatService);
        restServer.start();
    }

    private void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/events", this::handleEvents);
        server.createContext("/send", this::handleSend);
        server.createContext("/nick", this::handleNick);
        server.createContext("/join", this::handleJoin);
        server.createContext("/exit", this::handleExit);
        server.createContext("/rooms", this::handleRooms);
        server.createContext("/help", this::handleHelp);

        server.start();
        System.out.println("[REST] Chat server started on http://localhost:" + PORT);
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        String clientId = params.get("clientId");
        String name = params.getOrDefault("name", DEFAULT_NAME);
        String room = params.getOrDefault("room", DEFAULT_ROOM);

        if (clientId == null || clientId.isBlank()) {
            writeTextResponse(exchange, 400, "clientId is required");
            return;
        }

        disconnectExistingSession(clientId);
        RestSession session = openSseConnection(exchange, clientId, name, room);
        chatService.joinRoom(room, session);
        session.send("SERVER: connected. room=" + room + " name=" + name + " | commands: " + COMMANDS_HINT);

        waitUntilClosed(session);
        cleanupSession(clientId, exchange);
    }

    private void handleSend(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        RestSession session = findSessionOrRespond(exchange, params.get("clientId"));
        if (session == null) {
            return;
        }

        String text = params.getOrDefault("text", "").trim();
        if (!text.isEmpty()) {
            chatService.sendChatMessage(session.getRoom(), session.getName(), text);
        }

        writeTextResponse(exchange, 200, "ok");
    }

    private void handleNick(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        RestSession session = findSessionOrRespond(exchange, params.get("clientId"));
        if (session == null) {
            return;
        }

        String newName = params.getOrDefault("name", "").trim();
        if (newName.isEmpty()) {
            writeTextResponse(exchange, 400, "name must not be blank");
            return;
        }

        String oldName = session.getName();
        session.setName(newName);
        chatService.sendChatMessage(session.getRoom(), "SERVER", oldName + " is now " + newName);

        writeTextResponse(exchange, 200, "ok");
    }

    private void handleJoin(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        RestSession session = findSessionOrRespond(exchange, params.get("clientId"));
        if (session == null) {
            return;
        }

        String newRoom = params.getOrDefault("room", "").trim();
        if (newRoom.isEmpty()) {
            writeTextResponse(exchange, 400, "room must not be blank");
            return;
        }

        chatService.joinRoom(newRoom, session);
        session.send("SERVER: switched to room=" + session.getRoom());

        writeTextResponse(exchange, 200, "ok");
    }

    private void handleExit(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "POST")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        String clientId = params.get("clientId");

        RestSession session = sessionsByClientId.remove(clientId);
        if (session != null) {
            chatService.leaveRoom(session.getRoom(), session);
            session.close();
        }

        writeTextResponse(exchange, 200, "ok");
    }

    private void handleRooms(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        RestSession session = findSessionOrRespond(exchange, params.get("clientId"));
        if (session == null) {
            return;
        }

        chatService.sendRoomList(session);
        writeTextResponse(exchange, 200, "ok");
    }

    private void handleHelp(HttpExchange exchange) throws IOException {
        if (!requireMethod(exchange, "GET")) {
            return;
        }

        Map<String, String> params = parseQueryParams(exchange);
        RestSession session = findSessionOrRespond(exchange, params.get("clientId"));
        if (session == null) {
            return;
        }

        chatService.sendHelp(session);
        writeTextResponse(exchange, 200, "ok");
    }

    private void disconnectExistingSession(String clientId) {
        RestSession oldSession = sessionsByClientId.remove(clientId);
        if (oldSession != null) {
            chatService.leaveRoom(oldSession.getRoom(), oldSession);
            oldSession.close();
        }
    }

    private RestSession openSseConnection(HttpExchange exchange, String clientId, String name, String room) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        PrintWriter out = new PrintWriter(exchange.getResponseBody(), true, StandardCharsets.UTF_8);
        RestSession session = new RestSession(clientId, name, room, out);
        sessionsByClientId.put(clientId, session);
        return session;
    }

    private void waitUntilClosed(RestSession session) {
        try {
            while (!session.isClosed()) {
                Thread.sleep(1_000);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupSession(String clientId, HttpExchange exchange) {
        RestSession removed = sessionsByClientId.remove(clientId);
        if (removed != null) {
            chatService.leaveRoom(removed.getRoom(), removed);
            removed.close();
        }
        exchange.close();
    }

    private RestSession findSessionOrRespond(HttpExchange exchange, String clientId) throws IOException {
        RestSession session = sessionsByClientId.get(clientId);
        if (session == null) {
            writeTextResponse(exchange, 404, "session not found");
        }
        return session;
    }

    private static boolean requireMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }

        exchange.getResponseHeaders().set("Allow", expectedMethod.toUpperCase());
        writeTextResponse(exchange, 405, "method not allowed");
        return false;
    }

    private static void writeTextResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static Map<String, String> parseQueryParams(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }

        for (String pair : rawQuery.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) {
                continue;
            }

            String key = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }

        return map;
    }
}