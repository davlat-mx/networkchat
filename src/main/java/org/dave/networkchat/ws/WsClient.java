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

    private static final String HOST = "ws://localhost:8082/chat";
    private static final String DEFAULT_NAME = "WsUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "Commands: /nick NAME, /exit";

    public static void main(String[] args) throws Exception {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.print("Enter your name: ");
            String name = console.readLine();
            if (name == null || name.isBlank()) {
                name = DEFAULT_NAME;
            }

            System.out.print("Enter room (default: general): ");
            String room = console.readLine();
            if (room == null || room.isBlank()) {
                room = DEFAULT_ROOM;
            }

            String url = HOST + "?name=" + urlEncode(name) + "&room=" + urlEncode(room);

            HttpClient client = HttpClient.newHttpClient();

            WebSocket ws = client.newWebSocketBuilder()
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
                        System.out.println("[WS-CLIENT] Error: " + error.getMessage());
                    }
                }).join();

            System.out.println(COMMANDS_HINT);

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
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}