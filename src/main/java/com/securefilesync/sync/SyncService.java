package com.securefilesync.sync;

import com.securefilesync.crypto.CryptoService;
import com.securefilesync.crypto.EncryptedHeader;
import com.securefilesync.model.FileRecord;
import com.securefilesync.storage.SyncMetadataStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SyncService {
    private final Path sourceRoot;
    private final Path backupRoot;
    private final Path dbFile;
    private final char[] password;
    private final CryptoService cryptoService = new CryptoService();

    public SyncService(Path sourceRoot, Path backupRoot, Path dbFile, char[] password) {
        this.sourceRoot = sourceRoot.toAbsolutePath().normalize();
        this.backupRoot = backupRoot.toAbsolutePath().normalize();
        this.dbFile = dbFile.toAbsolutePath().normalize();
        this.password = password;
    }

    public synchronized SyncResult syncOnce() throws Exception {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("source folder does not exist: " + sourceRoot);
        }
        Files.createDirectories(backupRoot);

        SyncResult result = new SyncResult();
        try (SyncMetadataStore store = SyncMetadataStore.open(dbFile)) {
            Map<String, FileRecord> records = store.loadAll();
            Map<String, FileRecord> byHash = byHash(records);
            Set<String> seen = new HashSet<>();

            for (Path file : sourceFiles()) {
                processFile(file, records, byHash, seen, store, result);
            }

            for (FileRecord record : records.values()) {
                if (!seen.contains(record.relativePath())) {
                    store.delete(record.relativePath());
                    deleteObjectIfUnreferenced(store, record.encryptedPath());
                    result.recordDeleted();
                }
            }
        }
        return result;
    }

    private void processFile(
            Path file,
            Map<String, FileRecord> records,
            Map<String, FileRecord> byHash,
            Set<String> seen,
            SyncMetadataStore store,
            SyncResult result
    ) {
        String relativePath = relativePath(file);
        seen.add(relativePath);

        try {
            String sha256 = FileHasher.sha256Hex(file);
            long size = Files.size(file);
            FileTime lastModified = Files.getLastModifiedTime(file);
            FileRecord existing = records.get(relativePath);

            if (existing != null && existing.sha256Hex().equals(sha256) && Files.exists(safeBackupPath(existing.encryptedPath()))) {
                FileRecord refreshed = newRecord(relativePath, sha256, size, lastModified.toMillis(), existing.encryptedPath(), existing);
                store.upsert(refreshed);
                byHash.putIfAbsent(sha256, refreshed);
                result.recordSkipped();
                return;
            }

            EncryptedObject encryptedObject = encryptedObjectFor(file, sha256, size, byHash, result);
            FileRecord updated = new FileRecord(
                    relativePath,
                    sha256,
                    size,
                    lastModified.toMillis(),
                    encryptedObject.encryptedPath(),
                    encryptedObject.header().saltBase64(),
                    encryptedObject.header().nonceBase64(),
                    System.currentTimeMillis()
            );

            store.upsert(updated);
            byHash.put(sha256, updated);
            if (existing != null && !existing.encryptedPath().equals(updated.encryptedPath())) {
                deleteObjectIfUnreferenced(store, existing.encryptedPath());
            }
        } catch (Exception ex) {
            result.recordFailure(relativePath + ": " + ex.getMessage());
        }
    }

    private EncryptedObject encryptedObjectFor(
            Path file,
            String sha256,
            long size,
            Map<String, FileRecord> byHash,
            SyncResult result
    ) throws Exception {
        FileRecord reusable = byHash.get(sha256);
        if (reusable != null && Files.exists(safeBackupPath(reusable.encryptedPath()))) {
            result.recordDeduplicated();
            return new EncryptedObject(
                    reusable.encryptedPath(),
                    new EncryptedHeader(java.util.Base64.getDecoder().decode(reusable.saltBase64()),
                            java.util.Base64.getDecoder().decode(reusable.nonceBase64()))
            );
        }

        String encryptedPath = objectPath(sha256);
        Path target = safeBackupPath(encryptedPath);
        if (Files.exists(target)) {
            result.recordDeduplicated();
            return new EncryptedObject(encryptedPath, cryptoService.readHeader(target));
        }

        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            EncryptedHeader header = cryptoService.encryptFile(file, temp, password);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            result.recordEncrypted(size);
            return new EncryptedObject(encryptedPath, header);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<Path> sourceFiles() throws Exception {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(this::isSyncCandidate)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private boolean isSyncCandidate(Path path) {
        if (path.equals(dbFile)) {
            return false;
        }
        return !path.startsWith(backupRoot);
    }

    private FileRecord newRecord(String relativePath, String sha256, long size, long lastModifiedMillis, String encryptedPath, FileRecord existing) {
        return new FileRecord(
                relativePath,
                sha256,
                size,
                lastModifiedMillis,
                encryptedPath,
                existing.saltBase64(),
                existing.nonceBase64(),
                System.currentTimeMillis()
        );
    }

    private Map<String, FileRecord> byHash(Map<String, FileRecord> records) {
        Map<String, FileRecord> byHash = new HashMap<>();
        for (FileRecord record : records.values()) {
            byHash.putIfAbsent(record.sha256Hex(), record);
        }
        return byHash;
    }

    private String relativePath(Path file) {
        return sourceRoot.relativize(file).toString().replace('\\', '/');
    }

    private String objectPath(String sha256) {
        return "objects/" + sha256.substring(0, 2) + "/" + sha256 + ".sfs";
    }

    private Path safeBackupPath(String relativePath) {
        Path resolved = backupRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(backupRoot)) {
            throw new IllegalArgumentException("refusing to access backup path outside backup root: " + relativePath);
        }
        return resolved;
    }

    private void deleteObjectIfUnreferenced(SyncMetadataStore store, String encryptedPath) throws Exception {
        if (!store.hasEncryptedPath(encryptedPath)) {
            Files.deleteIfExists(safeBackupPath(encryptedPath));
        }
    }

    private record EncryptedObject(String encryptedPath, EncryptedHeader header) {
    }
}
