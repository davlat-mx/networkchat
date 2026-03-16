package org.dave.networkchat.ws;

import org.dave.networkchat.core.service.ChatService;
import org.dave.networkchat.core.service.ClientSession;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WsChatServer extends WebSocketServer {

    private static final String DEFAULT_NAME = "WsUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "/nick NAME, /join ROOM, /rooms, /help, /exit";

    private final ChatService chatService;
    private final Map<WebSocket, WsSession> sessions = new ConcurrentHashMap<>();

    public WsChatServer(InetSocketAddress address, ChatService chatService) {
        super(address);
        this.chatService = chatService;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Map<String, String> params = parseQueryParams(handshake.getResourceDescriptor());

        String name = params.getOrDefault("name", DEFAULT_NAME);
        String room = params.getOrDefault("room", DEFAULT_ROOM);

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

        if (message.equalsIgnoreCase("/rooms")) {
            chatService.sendRoomList(session);
            return;
        }

        if (message.equalsIgnoreCase("/help")) {
            chatService.sendHelp(session);
            return;
        }

        if (tryHandleNick(session, message)) {
            return;
        }

        if (tryHandleJoin(session, message)) {
            return;
        }

        chatService.sendChatMessage(session.getRoom(), session.getName(), message);
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
    public void onStart() {
    }

    private boolean tryHandleNick(ClientSession session, String message) {
        if (!message.startsWith("/nick ")) {
            return false;
        }

        String newName = message.substring(6).trim();
        if (!newName.isBlank()) {
            String oldName = session.getName();
            session.setName(newName);
            chatService.sendChatMessage(session.getRoom(), "SERVER", oldName + " is now " + newName);
        }
        return true;
    }

    private boolean tryHandleJoin(ClientSession session, String message) {
        if (!message.startsWith("/join ")) {
            return false;
        }

        String newRoom = message.substring(6).trim();
        if (newRoom.isBlank()) {
            session.send("SERVER: room name must not be blank");
            return true;
        }

        chatService.joinRoom(newRoom, session);
        session.send("SERVER: switched to room=" + session.getRoom());
        return true;
    }

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

            String key = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }

        return map;
    }
}