package org.dave.networkchat.core.service;

import org.dave.networkchat.core.ChatHistory;
import org.dave.networkchat.core.ChatHistoryImpl;

public final class ChatServiceFactory {

    private static final ChatHistory SHARED_HISTORY_STORE = new ChatHistoryImpl();

    private ChatServiceFactory() {
    }

    public static ChatService createChatService() {
        return new ChatService(SHARED_HISTORY_STORE);
    }
}