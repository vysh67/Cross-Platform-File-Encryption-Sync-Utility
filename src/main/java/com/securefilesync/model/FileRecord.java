package com.securefilesync.model;

public record FileRecord(
        String relativePath,
        String sha256Hex,
        long sizeBytes,
        long lastModifiedMillis,
        String encryptedPath,
        String saltBase64,
        String nonceBase64,
        long updatedAtMillis
) {
}
