package org.dave.networkchat.ws;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

public class WsClient {

    private static final String HOST = "ws://" + System.getenv().getOrDefault("SERVER_HOST", "localhost") + ":8082/chat";
    private static final String DEFAULT_NAME = "WsUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "Commands: /nick NAME, /join ROOM, /rooms, /help, /exit";
    private static final String ERROR_LOG_PREFIX = "[WS-CLIENT] Error: ";

    public static void main(String[] args) throws Exception {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String name = readInput(console, "Enter your name: ", DEFAULT_NAME);
            String room = readInput(console, "Enter room (default: general): ", DEFAULT_ROOM);

            String url = HOST + "?name=" + urlEncode(name) + "&room=" + urlEncode(room);
            WebSocket ws = connectToServer(url);

            System.out.println(COMMANDS_HINT);
            processInput(console, ws);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        }
    }

    private static String readInput(BufferedReader console, String prompt, String defaultValue) throws Exception {
        System.out.print(prompt);
        String input = console.readLine();
        return (input == null || input.isBlank()) ? defaultValue : input.trim();
    }

    private static WebSocket connectToServer(String url) {
        HttpClient client = HttpClient.newHttpClient();

        return client.newWebSocketBuilder()
            .buildAsync(URI.create(url), new WebSocket.Listener() {

                @Override
                public void onOpen(WebSocket webSocket) {
                    System.out.println("[WS-CLIENT] Connected to " + url);
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    System.out.println(data);
                    webSocket.request(1);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    System.out.println(ERROR_LOG_PREFIX + error.getMessage());
                }
            }).join();
    }

    private static void processInput(BufferedReader console, WebSocket ws) throws Exception {
        while (true) {
            String line = console.readLine();
            if (line == null) {
                break;
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            ws.sendText(line, true).join();

            if (line.equalsIgnoreCase("/exit")) {
                break;
            }
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}