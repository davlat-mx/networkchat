package org.dave.networkchat.rest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class RestClient {

    private static final String BASE_URL = "http://" + System.getenv().getOrDefault("SERVER_HOST", "localhost") + ":8081";
    private static final String DEFAULT_NAME = "RestUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "Commands: /nick NAME, /room, /exit";
    private static final String ERROR_LOG_PREFIX = "[REST-CLIENT] Error: ";
    private static final String SEND_ERROR_LOG_PREFIX = "[REST-CLIENT] Send failed: ";

    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();

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

            AtomicReference<String> nameRef = new AtomicReference<>(name);

            String eventsUrl = BASE_URL + "/events?name=" + urlEncode(nameRef.get()) + "&room=" + urlEncode(room);
            Thread eventsThread = new Thread(() -> listenToServerEvents(client, eventsUrl));

            eventsThread.setDaemon(true);
            eventsThread.start();

            System.out.println("[REST-CLIENT] Connected to " + BASE_URL);
            System.out.println(COMMANDS_HINT);
            System.out.println("Current room: " + room);

            while (true) {
                String text = console.readLine();
                if (text == null) {
                    break;
                }

                text = text.trim();
                if (text.isEmpty()) {
                    continue;
                }

                if (text.equalsIgnoreCase("/exit")) {
                    break;
                }

                if (text.equalsIgnoreCase("/room")) {
                    System.out.println("Current room: " + room);
                    continue;
                }

                if (text.startsWith("/nick ")) {
                    String newName = text.substring(6).trim();
                    if (!newName.isBlank()) {
                        nameRef.set(newName);
                        System.out.println("Nick changed to: " + newName);
                    }
                    continue;
                }

                String sendUrl = BASE_URL + "/send?name=" + urlEncode(nameRef.get())
                    + "&room=" + urlEncode(room)
                    + "&text=" + urlEncode(text);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sendUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

                try {
                    client.send(request, HttpResponse.BodyHandlers.discarding());
                } catch (Exception e) {
                    System.out.println(SEND_ERROR_LOG_PREFIX + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println(ERROR_LOG_PREFIX + e.getMessage());
        }

        System.out.println("[REST-CLIENT] Stopped");
    }

    private static void listenToServerEvents(HttpClient client, String eventsUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(eventsUrl))
                .GET()
                .build();

            HttpResponse<java.io.InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        System.out.println(line.substring(6));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(ERROR_LOG_PREFIX + e.getMessage());
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}