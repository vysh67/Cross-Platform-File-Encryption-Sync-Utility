package com.securefilesync.crypto;

import java.util.Base64;

public record EncryptedHeader(byte[] salt, byte[] nonce) {
    public String saltBase64() {
        return Base64.getEncoder().encodeToString(salt);
    }

    public String nonceBase64() {
        return Base64.getEncoder().encodeToString(nonce);
    }
}
