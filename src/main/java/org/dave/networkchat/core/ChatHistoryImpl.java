package org.dave.networkchat.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.dave.networkchat.core.model.ChatMessage;

public class ChatHistoryImpl implements ChatHistory {

    private final Map<String, List<ChatMessage>> messagesByRoom = new ConcurrentHashMap<>();

    @Override
    public void save(ChatMessage message) {
        messagesByRoom
            .computeIfAbsent(message.getRoom(), ignored -> new CopyOnWriteArrayList<>())
            .add(message);
    }

    @Override
    public List<ChatMessage> findByRoom(String room) {
        List<ChatMessage> messages = messagesByRoom.get(room);
        if (messages == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(messages);
    }
}