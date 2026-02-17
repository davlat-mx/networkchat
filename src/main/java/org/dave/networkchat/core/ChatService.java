package org.dave.networkchat.core;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatService {

    private final Map<String, Set<ClientSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public void joinRoom(String room, ClientSession session) {
        leaveRoom(session.getRoom(), session);

        sessionsByRoom.computeIfAbsent(room, ignored -> ConcurrentHashMap.newKeySet()).add(session);

        session.setRoom(room);
        broadcast(room, "[" + session.getName() + "] joined room: " + room);
    }

    public void leaveRoom(String room, ClientSession session) {
        if (room == null) {
            return;
        }

        Set<ClientSession> sessions = sessionsByRoom.get(room);
        if (sessions == null) {
            return;
        }

        sessions.remove(session);
        broadcast(room, "[" + session.getName() + "] left room: " + room);
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
}