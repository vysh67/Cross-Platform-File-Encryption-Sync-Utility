package com.securefilesync.restore;

import com.securefilesync.crypto.CryptoService;
import com.securefilesync.model.FileRecord;
import com.securefilesync.storage.SyncMetadataStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RestoreService {
    private final Path backupRoot;
    private final Path dbFile;
    private final Path targetRoot;
    private final char[] password;
    private final CryptoService cryptoService = new CryptoService();

    public RestoreService(Path backupRoot, Path dbFile, Path targetRoot, char[] password) {
        this.backupRoot = backupRoot.toAbsolutePath().normalize();
        this.dbFile = dbFile.toAbsolutePath().normalize();
        this.targetRoot = targetRoot.toAbsolutePath().normalize();
        this.password = password;
    }

    public RestoreResult restoreAll() throws Exception {
        if (!Files.isDirectory(backupRoot)) {
            throw new IllegalArgumentException("backup folder does not exist: " + backupRoot);
        }
        Files.createDirectories(targetRoot);

        RestoreResult result = new RestoreResult();
        try (SyncMetadataStore store = SyncMetadataStore.open(dbFile)) {
            Map<String, FileRecord> records = store.loadAll();
            for (FileRecord record : records.values()) {
                try {
                    Path encrypted = safeResolve(backupRoot, record.encryptedPath());
                    Path target = safeResolve(targetRoot, record.relativePath());
                    cryptoService.decryptFile(encrypted, target, password);
                    result.recordRestored();
                } catch (Exception ex) {
                    result.recordFailure(record.relativePath() + ": " + ex.getMessage());
                }
            }
        }
        return result;
    }

    private Path safeResolve(Path root, String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("refusing to access path outside root: " + relativePath);
        }
        return resolved;
    }
}
