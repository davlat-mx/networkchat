package org.dave.networkchat.tcp;

import org.dave.networkchat.core.service.ChatService;
import org.dave.networkchat.core.service.ClientSession;

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
    private static final String COMMANDS_HINT = "/join ROOM, /rooms, /nick NAME, /help, /exit";

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
            processMessages();
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    private void processMessages() throws IOException {
        String line;
        while ((line = socketReader.readLine()) != null) {
            if (line.equalsIgnoreCase("/exit")) {
                break;
            }
            if (tryHandleJoin(line)) {
                continue;
            }
            if (tryHandleNick(line)) {
                continue;
            }
            if (line.equalsIgnoreCase("/rooms")) {
                chatService.sendRoomList(this);
                continue;
            }
            if (line.equalsIgnoreCase("/help")) {
                chatService.sendHelp(this);
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            chatService.sendChatMessage(room, name, line);
        }
    }

    private boolean tryHandleJoin(String line) {
        if (!line.startsWith("/join ")) {
            return false;
        }
        String newRoom = line.substring(6).trim();
        if (newRoom.isBlank()) {
            send("SERVER: room name must not be blank");
            return true;
        }
        chatService.joinRoom(newRoom, this);
        send("SERVER: switched to room=" + room);
        return true;
    }

    private boolean tryHandleNick(String line) {
        if (!line.startsWith("/nick ")) {
            return false;
        }
        String newName = line.substring(6).trim();
        if (!newName.isBlank()) {
            String oldName = name;
            name = newName;
            chatService.sendChatMessage(room, "SERVER", oldName + " is now " + newName);
        }
        return true;
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