package org.dave.networkchat.ws;

import org.dave.networkchat.core.service.ClientSession;
import org.java_websocket.WebSocket;

import java.util.UUID;

public class WsSession implements ClientSession {

    private final String id = UUID.randomUUID().toString();
    private final WebSocket conn;

    private String name;
    private String room;

    public WsSession(WebSocket conn, String name, String room) {
        this.conn = conn;
        this.name = name;
        this.room = room;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getRoom() {
        return room;
    }

    @Override
    public void setRoom(String room) {
        this.room = room;
    }

    @Override
    public void send(String message) {
        conn.send(message);
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }
}