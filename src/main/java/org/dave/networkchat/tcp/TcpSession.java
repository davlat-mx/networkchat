package org.dave.networkchat.tcp;

import org.dave.networkchat.core.ChatService;
import org.dave.networkchat.core.ClientSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public class TcpSession implements Runnable, ClientSession {

    private static final String DEFAULT_NAME = "TcpUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "/join ROOM, /exit";

    private final Socket socket;
    private final BufferedReader socketReader;
    private final PrintWriter socketWriter;
    private final ChatService chatService;

    private final String id = UUID.randomUUID().toString();

    private String name = DEFAULT_NAME;
    private String room = DEFAULT_ROOM;

    public TcpSession(Socket socket, ChatService chatService) throws IOException {
        this.socket = socket;
        this.chatService = chatService;
        this.socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.socketWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    @Override
    public void run() {
        try {
            readClientProfile();

            chatService.joinRoom(room, this);
            send("SERVER: connected. room=" + room + " name=" + name + " | commands: " + COMMANDS_HINT);

            String line;
            while ((line = socketReader.readLine()) != null) {

                if (line.equalsIgnoreCase("/exit")) {
                    break;
                }

                if (line.startsWith("/join ")) {
                    String newRoom = line.substring(6).trim();
                    chatService.joinRoom(newRoom, this);
                    continue;
                }

                chatService.broadcast(room, "[" + name + "]: " + line);
            }

        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    private void readClientProfile() throws IOException {
        socketWriter.println("Enter your name:");
        String rawName = socketReader.readLine();
        if (rawName != null && !rawName.isBlank()) {
            name = rawName.trim();
        }

        socketWriter.println("Enter room (default: general):");
        String rawRoom = socketReader.readLine();
        if (rawRoom != null && !rawRoom.isBlank()) {
            room = rawRoom.trim();
        }
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
        socketWriter.println(message);
    }

    @Override
    public void close() {
        chatService.leaveRoom(room, this);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}