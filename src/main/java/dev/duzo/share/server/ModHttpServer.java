package dev.duzo.share.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.duzo.share.ModSync;
import dev.duzo.share.config.ModSyncConfig;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class ModHttpServer {

    private final int port;
    private HttpServer server;

    public ModHttpServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            server.createContext("/modsync/modlist", this::handleModList);
            server.createContext("/modsync/download", this::handleDownload);
            server.createContext("/modsync/ping", this::handlePing);

            server.start();
        } catch (IOException e) {
            ModSync.LOGGER.error("Failed to start ModSync HTTP server on port {}", port, e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(2);
        }
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        String response = "{\"status\":\"ok\",\"modsync\":true}";
        sendJsonResponse(exchange, 200, response);
    }

    private void handleModList(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        String json = ServerModScanner.getModListJson();
        sendJsonResponse(exchange, 200, json);
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        if (!checkAuth(exchange)) return;

        // Parse query parameter: ?file=<filename>
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String fileName = params.get("file");

        if (fileName == null || fileName.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Missing 'file' parameter\"}");
            return;
        }

        // Sanitize filename to prevent path traversal
        fileName = new File(fileName).getName();

        ServerModScanner.ModInfo mod = ServerModScanner.getModByFileName(fileName);
        if (mod == null) {
            sendJsonResponse(exchange, 404, "{\"error\":\"Mod not found\"}");
            return;
        }

        byte[] fileBytes = Files.readAllBytes(mod.filePath);

        exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
        exchange.getResponseHeaders().set("Content-Disposition",
            "attachment; filename=\"" + mod.fileName + "\"");
        exchange.getResponseHeaders().set("X-SHA256", mod.sha256);
        exchange.sendResponseHeaders(200, fileBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }

        ModSync.LOGGER.info("ModSync: Served mod JAR '{}' ({} bytes)", mod.fileName, fileBytes.length);
    }

    private boolean checkAuth(HttpExchange exchange) throws IOException {
        String secret = ModSyncConfig.SERVER_SECRET.get();
        if (secret == null || secret.isEmpty()) return true;

        String authHeader = exchange.getRequestHeaders().getFirst("X-ModSync-Auth");
        if (!secret.equals(authHeader)) {
            sendJsonResponse(exchange, 403, "{\"error\":\"Unauthorized\"}");
            return false;
        }
        return true;
    }

    private void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                params.put(
                    URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }
}