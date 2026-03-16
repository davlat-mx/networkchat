package org.dave.networkchat.tcp;

import org.dave.networkchat.core.service.ChatService;
import org.dave.networkchat.core.service.ChatServiceFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer {

    private static final int PORT = 8083;
    private static final String STARTUP_LOG_TEMPLATE = "[TCP] Chat server started on tcp://localhost:";

    public static void main(String[] args) throws IOException {
        ChatService chatService = ChatServiceFactory.createChatService();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println(STARTUP_LOG_TEMPLATE + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                TcpSession session = new TcpSession(socket, chatService);

                new Thread(session).start();
            }
        }
    }
}