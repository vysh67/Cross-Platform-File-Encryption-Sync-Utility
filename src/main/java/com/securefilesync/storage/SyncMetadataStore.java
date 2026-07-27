package com.securefilesync.storage;

import com.securefilesync.model.FileRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SyncMetadataStore implements AutoCloseable {
    private final Connection connection;

    private SyncMetadataStore(Connection connection) {
        this.connection = connection;
    }

    public static SyncMetadataStore open(Path dbFile) throws SQLException, IOException {
        Path absoluteDb = dbFile.toAbsolutePath().normalize();
        Path parent = absoluteDb.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("SQLite JDBC driver is missing. Build with Maven so sqlite-jdbc is on the runtime classpath.", ex);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absoluteDb);
        SyncMetadataStore store = new SyncMetadataStore(connection);
        store.initialize();
        return store;
    }

    public Map<String, FileRecord> loadAll() throws SQLException {
        String sql = """
                SELECT relative_path, sha256_hex, size_bytes, last_modified_millis,
                       encrypted_path, salt_b64, nonce_b64, updated_at_millis
                FROM file_manifest
                ORDER BY relative_path
                """;
        Map<String, FileRecord> records = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                FileRecord record = new FileRecord(
                        resultSet.getString("relative_path"),
                        resultSet.getString("sha256_hex"),
                        resultSet.getLong("size_bytes"),
                        resultSet.getLong("last_modified_millis"),
                        resultSet.getString("encrypted_path"),
                        resultSet.getString("salt_b64"),
                        resultSet.getString("nonce_b64"),
                        resultSet.getLong("updated_at_millis")
                );
                records.put(record.relativePath(), record);
            }
        }
        return records;
    }

    public void upsert(FileRecord record) throws SQLException {
        String sql = """
                INSERT INTO file_manifest (
                    relative_path, sha256_hex, size_bytes, last_modified_millis,
                    encrypted_path, salt_b64, nonce_b64, updated_at_millis
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(relative_path) DO UPDATE SET
                    sha256_hex = excluded.sha256_hex,
                    size_bytes = excluded.size_bytes,
                    last_modified_millis = excluded.last_modified_millis,
                    encrypted_path = excluded.encrypted_path,
                    salt_b64 = excluded.salt_b64,
                    nonce_b64 = excluded.nonce_b64,
                    updated_at_millis = excluded.updated_at_millis
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRecord(statement, record);
            statement.executeUpdate();
        }
    }

    public void delete(String relativePath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM file_manifest WHERE relative_path = ?")) {
            statement.setString(1, relativePath);
            statement.executeUpdate();
        }
    }

    public boolean hasEncryptedPath(String encryptedPath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM file_manifest WHERE encrypted_path = ? LIMIT 1")) {
            statement.setString(1, encryptedPath);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS file_manifest (
                        relative_path TEXT PRIMARY KEY NOT NULL,
                        sha256_hex TEXT NOT NULL,
                        size_bytes INTEGER NOT NULL,
                        last_modified_millis INTEGER NOT NULL,
                        encrypted_path TEXT NOT NULL,
                        salt_b64 TEXT NOT NULL,
                        nonce_b64 TEXT NOT NULL,
                        updated_at_millis INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_file_manifest_sha256 ON file_manifest(sha256_hex)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_file_manifest_encrypted_path ON file_manifest(encrypted_path)");
        }
    }

    private void bindRecord(PreparedStatement statement, FileRecord record) throws SQLException {
        statement.setString(1, record.relativePath());
        statement.setString(2, record.sha256Hex());
        statement.setLong(3, record.sizeBytes());
        statement.setLong(4, record.lastModifiedMillis());
        statement.setString(5, record.encryptedPath());
        statement.setString(6, record.saltBase64());
        statement.setString(7, record.nonceBase64());
        statement.setLong(8, record.updatedAtMillis());
    }
}
