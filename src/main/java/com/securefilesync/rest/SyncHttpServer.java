package com.securefilesync.rest;

import com.securefilesync.model.FileRecord;
import com.securefilesync.storage.SyncMetadataStore;
import com.securefilesync.sync.SyncResult;
import com.securefilesync.sync.SyncService;
import com.securefilesync.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

public final class SyncHttpServer {
    private final SyncService syncService;
    private final Path dbFile;
    private final HttpServer server;

    public SyncHttpServer(SyncService syncService, Path dbFile, int port) throws IOException {
        this.syncService = syncService;
        this.dbFile = dbFile.toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/health", this::handleHealth);
        this.server.createContext("/sync", this::handleSync);
        this.server.createContext("/files", this::handleFiles);
        this.server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(1);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        send(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleSync(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        try {
            SyncResult result = syncService.syncOnce();
            send(exchange, 200, result.toJson());
        } catch (Exception ex) {
            send(exchange, 500, "{\"error\":" + Json.string(ex.getMessage()) + "}");
        }
    }

    private void handleFiles(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }

        try (SyncMetadataStore store = SyncMetadataStore.open(dbFile)) {
            Map<String, FileRecord> records = store.loadAll();
            StringBuilder json = new StringBuilder();
            json.append("{\"count\":").append(records.size()).append(",\"files\":[");
            boolean first = true;
            for (FileRecord record : records.values()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{")
                        .append("\"path\":").append(Json.string(record.relativePath())).append(',')
                        .append("\"sha256\":").append(Json.string(record.sha256Hex())).append(',')
                        .append("\"sizeBytes\":").append(record.sizeBytes()).append(',')
                        .append("\"encryptedPath\":").append(Json.string(record.encryptedPath()))
                        .append("}");
            }
            json.append("]}");
            send(exchange, 200, json.toString());
        } catch (Exception ex) {
            send(exchange, 500, "{\"error\":" + Json.string(ex.getMessage()) + "}");
        }
    }

    private void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
