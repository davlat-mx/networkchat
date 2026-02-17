package org.dave.networkchat.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dave.networkchat.core.ChatService;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class RestServer {

    private static final int PORT = 8081;
    private static final String DEFAULT_NAME = "RestUser";
    private static final String DEFAULT_ROOM = "general";
    private static final String COMMANDS_HINT = "/nick NAME, /room, /exit";

    public static void main(String[] args) throws Exception {
        ChatService chatService = new ChatService();
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/events", exchange -> {
            Map<String, String> queryParams = parseQueryParams(exchange);
            String name = queryParams.getOrDefault("name", DEFAULT_NAME);
            String room = queryParams.getOrDefault("room", DEFAULT_ROOM);

            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");

            exchange.sendResponseHeaders(200, 0);

            PrintWriter out = new PrintWriter(exchange.getResponseBody(), true, StandardCharsets.UTF_8);
            RestSession session = new RestSession(name, room, out);

            chatService.joinRoom(room, session);
            session.send("SERVER: connected. room=" + room + " name=" + name + " | commands: " + COMMANDS_HINT);

            try {
                while (true) {
                    Thread.sleep(10_000);
                }
            } catch (Exception ignored) {
            } finally {
                session.close();
                chatService.leaveRoom(session.getRoom(), session);
                exchange.close();
            }
        });

        server.createContext("/send", exchange -> {
            Map<String, String> queryParams = parseQueryParams(exchange);
            String name = queryParams.getOrDefault("name", DEFAULT_NAME);
            String room = queryParams.getOrDefault("room", DEFAULT_ROOM);
            String text = queryParams.getOrDefault("text", "");

            chatService.broadcast(room, "[" + name + "]: " + text);

            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });

        server.start();
        System.out.println("[REST] Chat server started on http://localhost:" + PORT);
    }

    private static Map<String, String> parseQueryParams(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }

        for (String pair : rawQuery.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) {
                continue;
            }
            String key = urlDecode(pair.substring(0, i));
            String value = urlDecode(pair.substring(i + 1));
            map.put(key, value);
        }
        return map;
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}