package org.dave.networkchat.rest;

import org.dave.networkchat.core.service.ClientSession;

import java.io.PrintWriter;
import java.util.UUID;

public class RestSession implements ClientSession {

    private final String id;
    private final PrintWriter out;

    private volatile boolean closed;
    private String name;
    private String room;

    public RestSession(String id, String name, String room, PrintWriter out) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name;
        this.room = room;
        this.out = out;
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
        if (closed) {
            return;
        }

        out.print("data: " + message + "\n\n");
        out.flush();

        if (out.checkError()) {
            closed = true;
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            out.close();
        } catch (Exception ignored) {
        }
    }

    public boolean isClosed() {
        return closed;
    }
}