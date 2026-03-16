package org.dave.networkchat.ws;

import org.dave.networkchat.core.service.ChatService;
import org.dave.networkchat.core.service.ChatServiceFactory;

import java.net.InetSocketAddress;

public class WsServer {

    private static final int PORT = 8082;

    public static void main(String[] args) throws Exception {
        ChatService chatService = ChatServiceFactory.createChatService();
        WsChatServer server = new WsChatServer(new InetSocketAddress(PORT), chatService);

        server.start();
        System.out.println("[WS] Chat server started on ws://localhost:" + PORT + "/chat?name=WsUser&room=general");
    }
}