package com.securefilesync;

import com.securefilesync.restore.RestoreResult;
import com.securefilesync.restore.RestoreService;
import com.securefilesync.rest.SyncHttpServer;
import com.securefilesync.sync.SyncResult;
import com.securefilesync.sync.SyncService;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class App {
    private static final String DEFAULT_PASSWORD_ENV = "SECURE_SYNC_PASSWORD";

    private App() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printUsage(System.out);
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        Map<String, String> options = parseOptions(Arrays.copyOfRange(args, 1, args.length));

        try {
            switch (command) {
                case "sync" -> runSync(options);
                case "daemon" -> runDaemon(options);
                case "restore" -> runRestore(options);
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage(System.err);
                    System.exit(2);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.err.println("Error: " + ex.getMessage());
            printUsage(System.err);
            System.exit(2);
        }
    }

    private static void runSync(Map<String, String> options) throws Exception {
        char[] password = passwordFrom(options);
        try {
            SyncService service = new SyncService(
                    requirePath(options, "source"),
                    requirePath(options, "backup"),
                    requirePath(options, "db"),
                    password
            );
            SyncResult result = service.syncOnce();
            System.out.println(result.toJson());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void runDaemon(Map<String, String> options) throws Exception {
        char[] password = passwordFrom(options);
        SyncService service = new SyncService(
                requirePath(options, "source"),
                requirePath(options, "backup"),
                requirePath(options, "db"),
                password
        );

        long intervalSeconds = parseLong(options, "interval-seconds", 60L);
        int apiPort = (int) parseLong(options, "api-port", 8080L);
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("--interval-seconds must be at least 1");
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "secure-file-sync-daemon");
            thread.setDaemon(false);
            return thread;
        });

        SyncHttpServer api = null;
        if (apiPort > 0) {
            api = new SyncHttpServer(service, requirePath(options, "db"), apiPort);
            api.start();
            System.out.println("REST API listening on http://localhost:" + apiPort);
        }

        SyncHttpServer finalApi = api;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalApi != null) {
                finalApi.stop();
            }
            scheduler.shutdownNow();
            Arrays.fill(password, '\0');
        }, "secure-file-sync-shutdown"));

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                SyncResult result = service.syncOnce();
                System.out.println(result.summaryLine());
            } catch (Exception ex) {
                System.err.println("Sync failed: " + ex.getMessage());
            }
        }, 0L, intervalSeconds, TimeUnit.SECONDS);

        new CountDownLatch(1).await();
    }

    private static void runRestore(Map<String, String> options) throws Exception {
        char[] password = passwordFrom(options);
        try {
            RestoreService service = new RestoreService(
                    requirePath(options, "backup"),
                    requirePath(options, "db"),
                    requirePath(options, "target"),
                    password
            );
            RestoreResult result = service.restoreAll();
            System.out.println(result.toJson());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static char[] passwordFrom(Map<String, String> options) {
        String directPassword = options.get("password");
        if (directPassword != null && !directPassword.isBlank()) {
            return directPassword.toCharArray();
        }

        String envName = options.getOrDefault("password-env", DEFAULT_PASSWORD_ENV);
        String envPassword = System.getenv(envName);
        if (envPassword == null || envPassword.isBlank()) {
            throw new IllegalArgumentException("set --password-env " + envName + " or pass --password for local testing");
        }
        return envPassword.toCharArray();
    }

    private static Path requirePath(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing --" + key);
        }
        return Path.of(value);
    }

    private static long parseLong(Map<String, String> options, String key, long defaultValue) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("--" + key + " must be a number");
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + token);
            }

            String key = token.substring(2);
            if (key.isBlank()) {
                throw new IllegalArgumentException("empty option name");
            }

            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                options.put(key, "true");
            } else {
                options.put(key, args[++i]);
            }
        }
        return options;
    }

    private static void printUsage(PrintStream out) {
        out.println("""
                Usage:
                  java -jar target/secure-file-sync-1.0.0.jar sync --source <folder> --backup <folder> --db <file.db> [--password-env SECURE_SYNC_PASSWORD]
                  java -jar target/secure-file-sync-1.0.0.jar daemon --source <folder> --backup <folder> --db <file.db> [--interval-seconds 60] [--api-port 8080]
                  java -jar target/secure-file-sync-1.0.0.jar restore --backup <folder> --db <file.db> --target <folder> [--password-env SECURE_SYNC_PASSWORD]

                Commands:
                  sync      Encrypt and upload changed files once.
                  daemon    Run scheduled sync in the foreground and expose REST control endpoints.
                  restore   Decrypt files from the encrypted backup using the SQLite manifest.
                """);
    }
}
