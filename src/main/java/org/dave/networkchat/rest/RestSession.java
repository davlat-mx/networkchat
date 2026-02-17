package org.dave.networkchat.rest;

import org.dave.networkchat.core.ClientSession;

import java.io.PrintWriter;
import java.util.UUID;

public class RestSession implements ClientSession {

    private final String id = UUID.randomUUID().toString();
    private final PrintWriter out;

    private String name;
    private String room;

    public RestSession(String name, String room, PrintWriter out) {
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
        out.print("data: " + message + "\n\n");
        out.flush();
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (Exception ignored) {
        }
    }
}