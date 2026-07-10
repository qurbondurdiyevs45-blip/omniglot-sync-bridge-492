package com.omniglot.sync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * OmniGlot Sync-Bridge: Enterprise Relay Node.
 * Manages the mesh state for cross-platform synchronization.
 */
public class SyncServer {
    private static final Logger LOGGER = Logger.getLogger(SyncServer.class.getName());
    private static final int PORT = 8080;
    private static final Map<String, String> syncStore = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/sync/config", new ConfigHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        
        LOGGER.info("OmniGlot Sync-Bridge Relay started on port " + PORT);
        server.start();
    }

    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            try {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing request", e);
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{");
            String entries = syncStore.entrySet().stream()
                    .map(e -> String.format("\"%s\":\"%s\"", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(","));
            json.append(entries).append("}");

            byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            String body = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            // Simple key=value parser for robust P2P ingestion
            if (body.contains("=")) {
                String[] parts = body.split("=", 2);
                syncStore.put(parts[0].trim(), parts[1].trim());
                LOGGER.info("Updated mesh state: " + parts[0]);
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"UP\",\"mesh_nodes\":1}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        }
    }
}