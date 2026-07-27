package com.securefilesync.crypto;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class CryptoService {
    private static final byte[] MAGIC = new byte[]{'S', 'F', 'S', '1'};
    private static final int VERSION = 1;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int BUFFER_BYTES = 64 * 1024;

    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedHeader encryptFile(Path input, Path output, char[] password)
            throws IOException, GeneralSecurityException {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(NONCE_BYTES);
        Cipher cipher = encryptionCipher(password, salt, nonce);

        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (InputStream in = Files.newInputStream(input);
             OutputStream rawOut = Files.newOutputStream(output);
             DataOutputStream headerOut = new DataOutputStream(rawOut)) {
            writeHeader(headerOut, salt, nonce);
            try (CipherOutputStream cipherOut = new CipherOutputStream(rawOut, cipher)) {
                copy(in, cipherOut);
            }
        }

        return new EncryptedHeader(salt, nonce);
    }

    public void decryptFile(Path input, Path output, char[] password)
            throws IOException, GeneralSecurityException {
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        try (InputStream rawIn = Files.newInputStream(input);
             DataInputStream headerIn = new DataInputStream(rawIn)) {
            EncryptedHeader header = readHeader(headerIn);
            Cipher cipher = decryptionCipher(password, header.salt(), header.nonce());
            try (CipherInputStream cipherIn = new CipherInputStream(rawIn, cipher);
                 OutputStream out = Files.newOutputStream(output)) {
                copy(cipherIn, out);
            }
        }
    }

    public EncryptedHeader readHeader(Path input) throws IOException {
        try (InputStream rawIn = Files.newInputStream(input);
             DataInputStream headerIn = new DataInputStream(rawIn)) {
            return readHeader(headerIn);
        }
    }

    private Cipher encryptionCipher(char[] password, byte[] salt, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher;
    }

    private Cipher decryptionCipher(char[] password, byte[] salt, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), new GCMParameterSpec(TAG_BITS, nonce));
        return cipher;
    }

    private SecretKeySpec deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            byte[] keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private void writeHeader(DataOutputStream out, byte[] salt, byte[] nonce) throws IOException {
        out.write(MAGIC);
        out.writeByte(VERSION);
        out.writeInt(salt.length);
        out.writeInt(nonce.length);
        out.write(salt);
        out.write(nonce);
        out.flush();
    }

    private EncryptedHeader readHeader(DataInputStream in) throws IOException {
        byte[] magic = in.readNBytes(MAGIC.length);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("not a secure-file-sync encrypted object");
        }

        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("unsupported encrypted object version: " + version);
        }

        int saltLength = in.readInt();
        int nonceLength = in.readInt();
        if (saltLength <= 0 || saltLength > 64 || nonceLength <= 0 || nonceLength > 32) {
            throw new IOException("invalid encrypted object header");
        }

        byte[] salt = in.readNBytes(saltLength);
        byte[] nonce = in.readNBytes(nonceLength);
        if (salt.length != saltLength || nonce.length != nonceLength) {
            throw new EOFException("truncated encrypted object header");
        }
        return new EncryptedHeader(salt, nonce);
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }
}
