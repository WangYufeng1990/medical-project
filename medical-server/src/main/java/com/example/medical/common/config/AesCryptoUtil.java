package com.example.medical.common.config;

import com.example.medical.common.audit.KeyAudit;
import com.example.medical.common.audit.KeyAuditRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Slf4j
@Component
public class AesCryptoUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom RNG = new SecureRandom();
    private static final byte VERSION_CURRENT = 0x01;

    @Value("${app.aes.key}")
    private String configuredKey;

    @Value("${app.aes.key.previous:}")
    private String configuredPreviousKey;

    private static SecretKey CURRENT_KEY;
    private static SecretKey PREVIOUS_KEY;
    private static boolean rotationActive;
    private static KeyAuditRepository keyAuditRepo;

    static void setKeyAuditRepository(KeyAuditRepository repo) {
        keyAuditRepo = repo;
    }

    static void initializeForTest(String key) {
        initializeForTest(key, null);
    }

    static void initializeForTest(String key, String previousKey) {
        try {
            CURRENT_KEY = deriveKey(key);
            PREVIOUS_KEY = previousKey != null ? deriveKey(previousKey) : null;
            rotationActive = PREVIOUS_KEY != null;
        } catch (Exception e) {
            throw new RuntimeException("Test key initialization failed", e);
        }
    }

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    "app.aes.key is required but not configured. " +
                    "Generate with: openssl rand -base64 32");
        }
        try {
            CURRENT_KEY = deriveKey(configuredKey);
            boolean wasRotated = false;
            if (configuredPreviousKey != null && !configuredPreviousKey.isBlank()) {
                PREVIOUS_KEY = deriveKey(configuredPreviousKey);
                rotationActive = true;
                wasRotated = true;
                log.info("AES key rotation active: current=v1, previous=v0");
            } else {
                PREVIOUS_KEY = null;
                rotationActive = false;
                log.info("AES-GCM encryption key initialized (single-key mode)");
            }
            if (keyAuditRepo != null) {
                KeyAudit audit = new KeyAudit();
                audit.setEventType(wasRotated ? "KEY_ROTATION" : "KEY_INIT");
                audit.setKeyVersion(wasRotated ? "v1+v0" : "v1");
                audit.setDetail(wasRotated ? "Key rotation detected on startup" : "Single key initialized on startup");
                keyAuditRepo.save(audit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES encryption key", e);
        }
    }

    /**
     * Encrypt with the current key. Output is hex-encoded with a version byte prefix.
     * Format: [version:1B][IV:12B][ciphertext+tag:N B] → hex
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, CURRENT_KEY, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + GCM_IV_LENGTH + ciphertext.length);
            buffer.put(VERSION_CURRENT);
            buffer.put(iv);
            buffer.put(ciphertext);
            return bytesToHex(buffer.array());
        } catch (Exception e) {
            log.error("AES-GCM encryption failed — storing null", e);
            return null;
        }
    }

    /**
     * Decrypt data that may be either versioned (v1 prefix) or unversioned (legacy).
     * Unversioned data is tried against PREVIOUS_KEY first (if rotation is active),
     * then CURRENT_KEY as fallback.
     */
    public static String decrypt(String cipherHex) {
        if (cipherHex == null) return null;
        try {
            byte[] combined = hexToBytes(cipherHex);
            if (combined.length < GCM_IV_LENGTH + 1) {
                log.error("Ciphertext too short ({} bytes) — returning placeholder", combined.length);
                return "[DECRYPT_FAILED]";
            }

            byte version = combined[0];
            if (version == VERSION_CURRENT) {
                return decryptWithKey(combined, 1, CURRENT_KEY);
            }

            // Legacy unversioned data — first byte is part of the IV
            if (rotationActive) {
                try {
                    return decryptWithKey(combined, 0, PREVIOUS_KEY);
                } catch (Exception e) {
                    log.warn("Decryption with previous key failed, trying current key as fallback");
                }
            }
            return decryptWithKey(combined, 0, CURRENT_KEY);
        } catch (Exception e) {
            log.error("AES-GCM decryption failed — returning placeholder. "
                    + "This may indicate key rotation or data corruption.", e);
            return "[DECRYPT_FAILED]";
        }
    }

    private static String decryptWithKey(byte[] combined, int ivOffset, SecretKey key) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(combined, ivOffset, combined.length - ivOffset);
        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(String raw) {
        try {
            javax.crypto.SecretKeyFactory factory =
                    javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] salt = "medical-aes-v2-salt".getBytes(StandardCharsets.UTF_8);
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    raw.toCharArray(), salt, 310_000, 256);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    /**
     * Re-encrypt a legacy ciphertext with the current key.
     * Decrypts (automatically using PREVIOUS_KEY if unversioned),
     * then re-encrypts with CURRENT_KEY, producing a v1-prefixed ciphertext.
     * Returns null if the input is null or decryption fails.
     */
    public static String reencrypt(String cipherHex) {
        if (cipherHex == null) return null;
        String plaintext = decrypt(cipherHex);
        if (plaintext == null || plaintext.equals("[DECRYPT_FAILED]")) return null;
        return encrypt(plaintext);
    }

    /**
     * Returns true if key rotation is active (a previous key is configured).
     */
    public static boolean isRotationActive() {
        return rotationActive;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
