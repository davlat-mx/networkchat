package org.dave.networkchat.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpClient {

    private static final String HOST = System.getenv().getOrDefault("SERVER_HOST", "localhost");
    private static final int PORT = 8083;
    private static final String COMMANDS_HINT = "Commands: /join ROOM, /exit";
    private static final String ERROR_LOG_PREFIX = "[TCP-CLIENT] Error: ";

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("[TCP-CLIENT] Connected to tcp://" + HOST + ":" + PORT);
            System.out.println(COMMANDS_HINT);

            Thread reader = new Thread(() -> {
                try {
                    String message;
                    while ((message = serverIn.readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException ignored) {
                }
            });
            reader.start();

            String line;
            while ((line = consoleIn.readLine()) != null) {
                serverOut.println(line);
                if (line.equalsIgnoreCase("/exit")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println(ERROR_LOG_PREFIX + e.getMessage());
        }
    }
}