package org.dave.networkchat.core.service;

public interface ClientSession {

    String getId();

    String getName();

    void setName(String name);

    String getRoom();

    void setRoom(String room);

    void send(String message);

    void close();
}