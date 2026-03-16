package org.dave.networkchat.rest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class RestClient {

    private static final String BASE_URL = "http://" + System.getenv().getOrDefault("SERVER_HOST", "localhost") + ":8081";
    private static final String DEFAULT_NAME = "RestUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "Commands: /nick NAME, /join ROOM, /rooms, /room, /help, /exit";
    private static final String ERROR_LOG_PREFIX = "[REST-CLIENT] Error: ";
    private static final String SEND_ERROR_LOG_PREFIX = "[REST-CLIENT] Send failed: ";

    private final HttpClient client;
    private final String clientId;
    private final AtomicReference<String> nameRef = new AtomicReference<>();
    private final AtomicReference<String> roomRef = new AtomicReference<>();
    private final AtomicReference<Thread> eventsThreadRef = new AtomicReference<>();
    private final AtomicReference<InputStream> eventsStreamRef = new AtomicReference<>();

    public RestClient(HttpClient client, String clientId) {
        this.client = client;
        this.clientId = clientId;
    }

    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        String clientId = UUID.randomUUID().toString();
        RestClient restClient = new RestClient(client, clientId);

        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            restClient.run(console);
        } catch (Exception e) {
            System.out.println(ERROR_LOG_PREFIX + e.getMessage());
        }

        System.out.println("[REST-CLIENT] Stopped");
    }

    private void run(BufferedReader console) throws Exception {
        readProfile(console);
        connectToServer();
        processInput(console);
        shutdown();
    }

    private void readProfile(BufferedReader console) throws Exception {
        System.out.print("Enter your name: ");
        String name = console.readLine();
        nameRef.set(name == null || name.isBlank() ? DEFAULT_NAME : name.trim());

        System.out.print("Enter room (default: general): ");
        String room = console.readLine();
        roomRef.set(room == null || room.isBlank() ? DEFAULT_ROOM : room.trim());
    }

    private void connectToServer() {
        String eventsUrl = BASE_URL
            + "/events?clientId=" + urlEncode(clientId)
            + "&name=" + urlEncode(nameRef.get())
            + "&room=" + urlEncode(roomRef.get());

        Thread eventsThread = new Thread(() -> listenToServerEvents(eventsUrl));
        eventsThread.setDaemon(true);
        eventsThread.start();
        eventsThreadRef.set(eventsThread);

        System.out.println("[REST-CLIENT] Connected to " + BASE_URL);
        System.out.println(COMMANDS_HINT);
        System.out.println("Current room: " + roomRef.get());
    }

    private void processInput(BufferedReader console) throws Exception {
        while (true) {
            String text = console.readLine();
            if (text == null) {
                break;
            }

            text = text.trim();
            if (text.isEmpty()) {
                continue;
            }

            if (handleCommand(text)) {
                break;
            }
        }
    }

    private boolean handleCommand(String text) {
        if (text.equalsIgnoreCase("/exit")) {
            post(BASE_URL + "/exit?clientId=" + urlEncode(clientId));
            return true;
        }

        if (text.equalsIgnoreCase("/rooms")) {
            get(BASE_URL + "/rooms?clientId=" + urlEncode(clientId));
            return false;
        }

        if (text.equalsIgnoreCase("/help")) {
            get(BASE_URL + "/help?clientId=" + urlEncode(clientId));
            return false;
        }

        if (text.equalsIgnoreCase("/room")) {
            System.out.println("Current room: " + roomRef.get());
            return false;
        }

        if (text.startsWith("/nick ")) {
            handleNick(text.substring(6).trim());
            return false;
        }

        if (text.startsWith("/join ")) {
            handleJoin(text.substring(6).trim());
            return false;
        }

        sendMessage(text);
        return false;
    }

    private void handleNick(String newName) {
        if (newName.isBlank()) {
            return;
        }
        post(BASE_URL + "/nick?clientId=" + urlEncode(clientId) + "&name=" + urlEncode(newName));
        nameRef.set(newName);
        System.out.println("Nick changed to: " + newName);
    }

    private void handleJoin(String newRoom) {
        if (newRoom.isBlank()) {
            System.out.println("[REST-CLIENT] Room name must not be blank");
            return;
        }
        post(BASE_URL + "/join?clientId=" + urlEncode(clientId) + "&room=" + urlEncode(newRoom));
        roomRef.set(newRoom);
        System.out.println("Switched to room: " + newRoom);
    }

    private void sendMessage(String text) {
        post(BASE_URL + "/send?clientId=" + urlEncode(clientId) + "&text=" + urlEncode(text));
    }

    private void shutdown() {
        InputStream stream = eventsStreamRef.getAndSet(null);
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
        }

        Thread thread = eventsThreadRef.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void listenToServerEvents(String eventsUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(eventsUrl))
                .GET()
                .build();

            HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            eventsStreamRef.set(response.body());

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
        } finally {
            eventsStreamRef.set(null);
        }
    }

    private void post(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println(SEND_ERROR_LOG_PREFIX + e.getMessage());
        }
    }

    private void get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println(SEND_ERROR_LOG_PREFIX + e.getMessage());
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}