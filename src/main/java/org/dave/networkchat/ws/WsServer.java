package org.dave.networkchat.ws;

import org.dave.networkchat.core.ChatService;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WsServer {

    private static final int PORT = 8082;

    public static void main(String[] args) throws Exception {
        ChatService chatService = new ChatService();
        WsChatServer server = new WsChatServer(new InetSocketAddress(PORT), chatService);

        server.start();
        System.out.println("[WS] Chat server started on ws://localhost:" + PORT + "/chat?name=WsUser&room=general");
    }

    static class WsChatServer extends WebSocketServer {

        private static final String DEFAULT_NAME = "WsUser";
        private static final String DEFAULT_ROOM = "general";
        private static final String COMMANDS_HINT = "/nick NAME, /exit";

        private final ChatService chatService;
        private final Map<WebSocket, WsSession> sessions = new ConcurrentHashMap<>();

        WsChatServer(InetSocketAddress address, ChatService chatService) {
            super(address);
            this.chatService = chatService;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String resource = handshake.getResourceDescriptor();
            Map<String, String> queryParams = parseQueryParams(resource);

            String name = queryParams.getOrDefault("name", DEFAULT_NAME);
            String room = queryParams.getOrDefault("room", DEFAULT_ROOM);

            WsSession session = new WsSession(conn, name, room);
            sessions.put(conn, session);

            chatService.joinRoom(room, session);
            session.send("SERVER: connected. room=" + room + " name=" + name + " | commands: " + COMMANDS_HINT);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            WsSession session = sessions.get(conn);
            if (session == null) {
                return;
            }

            message = message == null ? "" : message.trim();
            if (message.isEmpty()) {
                return;
            }

            if (message.equalsIgnoreCase("/exit")) {
                conn.close();
                return;
            }

            if (message.startsWith("/nick ")) {
                String newName = message.substring(6).trim();
                if (!newName.isBlank()) {
                    String old = session.getName();
                    session.setName(newName);
                    chatService.broadcast(session.getRoom(), "SERVER: " + old + " is now " + newName);
                }
                return;
            }

            chatService.broadcast(session.getRoom(), "[" + session.getName() + "]: " + message);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            WsSession session = sessions.remove(conn);
            if (session != null) {
                chatService.leaveRoom(session.getRoom(), session);
                session.close();
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.out.println("[WS] Error: " + ex.getMessage());
        }

        @Override
        public void onStart() {}

        private Map<String, String> parseQueryParams(String resource) {
            Map<String, String> map = new HashMap<>();
            if (resource == null) {
                return map;
            }

            int qIndex = resource.indexOf('?');
            if (qIndex < 0) {
                return map;
            }

            String query = resource.substring(qIndex + 1);
            for (String pair : query.split("&")) {
                int i = pair.indexOf('=');
                if (i < 0) {
                    continue;
                }
                String key = urlDecode(pair.substring(0, i));
                String value = urlDecode(pair.substring(i + 1));
                map.put(key, value);
            }
            return map;
        }

        private String urlDecode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }
}