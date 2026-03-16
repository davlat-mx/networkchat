package org.dave.networkchat.core.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.dave.networkchat.core.ChatHistory;
import org.dave.networkchat.core.model.ChatMessage;
import org.dave.networkchat.core.model.ChatMessageType;

public class ChatService {

    private static final String SYSTEM_SENDER = "SERVER";
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, Set<ClientSession>> sessionsByRoom = new ConcurrentHashMap<>();
    private final ChatHistory chatHistory;

    public ChatService(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    public void joinRoom(String newRoom, ClientSession session) {
        leaveCurrentRoomIfSwitching(newRoom, session);
        addSessionToRoom(newRoom, session);
        sendRoomHistory(newRoom, session);
        saveAndBroadcast(newRoom, session.getName() + " joined room: " + newRoom);
    }

    public void leaveRoom(String room, ClientSession session) {
        if (room == null) {
            return;
        }

        boolean removed = removeSessionFromRoom(room, session);
        if (!removed) {
            return;
        }

        saveAndBroadcast(room, session.getName() + " left room: " + room);
    }

    public void sendChatMessage(String room, String sender, String text) {
        ChatMessage message = new ChatMessage(
            room, sender, text, System.currentTimeMillis(), ChatMessageType.CHAT
        );
        chatHistory.save(message);
        broadcast(room, formatMessage(message));
    }

    public List<String> getRoomNames() {
        List<String> rooms = new ArrayList<>(sessionsByRoom.keySet());
        Collections.sort(rooms);
        return rooms;
    }

    public void sendRoomList(ClientSession session) {
        List<String> rooms = getRoomNames();
        session.send("SERVER: active rooms (" + rooms.size() + "):");
        if (rooms.isEmpty()) {
            session.send("SERVER: no active rooms");
            return;
        }
        for (String room : rooms) {
            Set<ClientSession> members = sessionsByRoom.get(room);
            int count = members == null ? 0 : members.size();
            session.send("  - " + room + " (" + count + " users)");
        }
    }

    public void sendHelp(ClientSession session) {
        session.send("SERVER: available commands:");
        session.send("  /join ROOM  - join or switch to a room");
        session.send("  /rooms      - list all active rooms");
        session.send("  /nick NAME  - change your nickname");
        session.send("  /room       - show current room name");
        session.send("  /help       - show this help message");
        session.send("  /exit       - disconnect from chat");
    }

    public void broadcast(String room, String message) {
        Set<ClientSession> sessions = sessionsByRoom.get(room);
        if (sessions == null) {
            return;
        }

        for (ClientSession session : sessions) {
            session.send(message);
        }
    }

    private void leaveCurrentRoomIfSwitching(String newRoom, ClientSession session) {
        String oldRoom = session.getRoom();
        if (oldRoom != null && !oldRoom.equals(newRoom)) {
            removeSessionFromRoom(oldRoom, session);
            saveAndBroadcast(oldRoom, session.getName() + " left room: " + oldRoom);
        }
    }

    private void addSessionToRoom(String room, ClientSession session) {
        sessionsByRoom
            .computeIfAbsent(room, ignored -> ConcurrentHashMap.newKeySet())
            .add(session);
        session.setRoom(room);
    }

    private void sendRoomHistory(String room, ClientSession session) {
        List<ChatMessage> history = chatHistory.findByRoom(room);

        session.send("SERVER: chat history for room [" + room + "]");

        if (history.isEmpty()) {
            session.send("SERVER: no messages yet");
            return;
        }

        for (ChatMessage message : history) {
            session.send(formatMessage(message));
        }

        session.send("SERVER: end of history");
    }

    private void saveAndBroadcast(String room, String text) {
        ChatMessage message = buildSystemMessage(room, text);
        chatHistory.save(message);
        broadcast(room, formatMessage(message));
    }

    private boolean removeSessionFromRoom(String room, ClientSession session) {
        Set<ClientSession> sessions = sessionsByRoom.get(room);
        if (sessions == null) {
            return false;
        }

        boolean removed = sessions.remove(session);

        if (sessions.isEmpty()) {
            sessionsByRoom.remove(room);
        }

        return removed;
    }

    private ChatMessage buildSystemMessage(String room, String text) {
        return new ChatMessage(
            room, SYSTEM_SENDER, text, System.currentTimeMillis(), ChatMessageType.SYSTEM
        );
    }

    private String formatMessage(ChatMessage message) {
        String formattedTime = formatTimestamp(message.getTimestamp());

        if (message.getType() == ChatMessageType.SYSTEM) {
            return "[" + formattedTime + "] " + message.getSender() + ": " + message.getText();
        }

        return "[" + formattedTime + "] [" + message.getSender() + "]: " + message.getText();
    }

    private String formatTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        ).format(TIME_FORMATTER);
    }
}