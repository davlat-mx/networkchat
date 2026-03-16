package org.dave.networkchat.core;

import java.util.List;

import org.dave.networkchat.core.model.ChatMessage;

public interface ChatHistory {

    void save(ChatMessage message);

    List<ChatMessage> findByRoom(String room);
}