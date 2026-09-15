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
    /**
     * key_audit event holding just the rotated key's fingerprint (see
     * {@link #recordRuntimeRotation}). Keep it within key_audit.event_type's
     * VARCHAR(20) — a longer name fails the insert at runtime (verified the hard way).
     */
    private static final String FINGERPRINT_EVENT = "KEY_ROT_FINGERPRINT";

    @Value("${app.aes.key}")
    private String configuredKey;

    @Value("${app.aes.key.previous:}")
    private String configuredPreviousKey;

    private static SecretKey CURRENT_KEY;
    private static SecretKey PREVIOUS_KEY;
    private static boolean rotationActive;
    private static KeyAuditRepository keyAuditRepo;
    private static String currentRawKey;
    private static String previousRawKey;

    static void setKeyAuditRepository(KeyAuditRepository repo) {
        keyAuditRepo = repo;
    }

    // ── test-only key seam ──
    // Package-private on purpose: JPA instantiates the converter outside Spring, so
    // the key is process-wide static state and a test that swaps it must be able to
    // put the previous value back (see AesAttributeConverterTest). These are the only
    // entry points that bypass app.aes.key/rotate, and nothing in src/main calls them.

    static void initializeForTest(String key) {
        initializeForTest(key, null);
    }

    static void initializeForTest(String key, String previousKey) {
        try {
            CURRENT_KEY = deriveKey(key);
            PREVIOUS_KEY = previousKey != null ? deriveKey(previousKey) : null;
            rotationActive = PREVIOUS_KEY != null;
            currentRawKey = key;
            previousRawKey = previousKey;
        } catch (Exception e) {
            throw new RuntimeException("Test key initialization failed", e);
        }
    }

    /**
     * Process-wide key state. The keys are static, so a test that swaps them
     * changes encryption for every other test in the same JVM — snapshot first
     * and restore afterwards instead of leaving the JVM on test material.
     */
    record KeySnapshot(String current, String previous) {}

    static KeySnapshot snapshotKeysForTest() {
        return new KeySnapshot(currentRawKey, previousRawKey);
    }

    static void restoreKeysForTest(KeySnapshot snapshot) {
        // No key was configured yet (no Spring context has initialised one):
        // there is no prior state to put back, and the next init() sets it.
        if (snapshot.current() == null) return;
        if (java.util.Objects.equals(snapshot.current(), currentRawKey)
                && java.util.Objects.equals(snapshot.previous(), previousRawKey)) {
            return;
        }
        initializeForTest(snapshot.current(), snapshot.previous());
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
            currentRawKey = configuredKey;
            previousRawKey = null;
            if (configuredPreviousKey != null && !configuredPreviousKey.isBlank()) {
                PREVIOUS_KEY = deriveKey(configuredPreviousKey);
                previousRawKey = configuredPreviousKey;
                rotationActive = true;
                log.info("AES key rotation active: current=v1, previous=v0");
            } else {
                PREVIOUS_KEY = null;
                rotationActive = false;
                log.info("AES-GCM encryption key initialized (single-key mode)");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize AES encryption key", e);
        }
    }

    /**
     * Detects the Review III C2 operational hazard: a runtime rotation
     * (rotate()) whose env config (AES_KEY / AES_KEY_PREVIOUS) was never
     * updated. After restart the configured key would not match the rotated
     * key, making post-rotation ciphertext unreadable. Warns loudly instead
     * of silently corrupting.
     */
    /**
     * Records the startup key event and runs the restart-consistency check.
     * <p>
     * Called by {@link KeyAuditBridge} once the audit repository is wired, not from
     * {@link #init()}: a {@code @PostConstruct} here runs before the bridge, so
     * {@code keyAuditRepo} is still null and neither the KEY_INIT row nor the
     * mismatch check ever happened (verified — key_audit held no KEY_INIT rows).
     */
    void recordStartupAndCheckRotation() {
        if (keyAuditRepo == null) return;
        try {
            KeyAudit audit = new KeyAudit();
            audit.setEventType(rotationActive ? "KEY_ROTATION" : "KEY_INIT");
            audit.setKeyVersion(rotationActive ? "v1+v0" : "v1");
            audit.setDetail(rotationActive ? "Key rotation detected on startup" : "Single key initialized on startup");
            keyAuditRepo.save(audit);
            warnIfStaleConfigAfterRuntimeRotation();
        } catch (Exception e) {
            log.error("Failed to record the key startup audit", e);
        }
    }

    private void warnIfStaleConfigAfterRuntimeRotation() {
        try {
            keyAuditRepo.findTopByEventTypeOrderByIdDesc(FINGERPRINT_EVENT).ifPresent(last -> {
                String recorded = last.getDetail();
                if (recorded == null || recorded.isBlank()) return;
                String configured = fingerprint(configuredKey);
                if (!recorded.equals(configured)) {
                    log.error("KEY ROTATION CONFIG MISMATCH: key_audit records a runtime rotation with newKey "
                            + "fingerprint={} but the configured app.aes.key has fingerprint={}. Ciphertext written "
                            + "after that rotation is UNREADABLE unless AES_KEY is set to the rotated key and "
                            + "AES_KEY_PREVIOUS to the previous key. Update the environment now.",
                            recorded, configured);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to check key rotation config consistency", e);
        }
    }

    /**
     * Encrypt with the current key. Output is hex-encoded with a version byte prefix.
     * Format: [version:1B][IV:12B][ciphertext+tag:N B] → hex
     * <p>
     * Throws when encryption fails instead of returning null. Encryption cannot fail
     * for a single value — the only causes are process-wide (no key initialised, a
     * JCE provider problem, no memory) — so returning null would turn a broken
     * process into silently empty clinical fields: the write succeeds, the caller
     * is told 200, and the PHI is gone. Failing the write rolls the transaction
     * back instead. The read side keeps its placeholder behaviour on purpose:
     * a single unreadable row must not break a list endpoint, but a value that
     * cannot be encrypted must never be stored as if it were empty.
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
            throw new IllegalStateException(
                    "AES-GCM encryption failed — refusing to write the field. "
                    + "The encryption key is probably unset or the JCE provider is broken; "
                    + "storing a placeholder would silently lose this value.", e);
        }
    }

    /**
     * Decrypt data that may be either versioned (v1 prefix) or unversioned (legacy).
     * v1 rows are tried with CURRENT_KEY first, then PREVIOUS_KEY — after a
     * rotation, existing rows are still encrypted with the old key while new
     * writes use the new one, and both carry the 0x01 version byte
     * (Review III C2). Unversioned data is tried against PREVIOUS_KEY first
     * (if rotation is active), then CURRENT_KEY.
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
                try {
                    return decryptWithKey(combined, 1, CURRENT_KEY);
                } catch (Exception currentFailure) {
                    if (PREVIOUS_KEY != null) {
                        try {
                            return decryptWithKey(combined, 1, PREVIOUS_KEY);
                        } catch (Exception previousFailure) {
                            log.error("AES-GCM decryption failed with both current and previous keys — "
                                    + "returning placeholder. Key mismatch or data corruption.", previousFailure);
                            return "[DECRYPT_FAILED]";
                        }
                    }
                    log.error("AES-GCM decryption failed — returning placeholder. "
                            + "This may indicate key rotation or data corruption.", currentFailure);
                    return "[DECRYPT_FAILED]";
                }
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
     * True when the ciphertext was encrypted with the PREVIOUS key (versioned
     * or legacy) — i.e. the row still needs migration after a rotation.
     * Used by {@code KeyRotationService} (Review III C2).
     */
    public static boolean isEncryptedWithPreviousKey(String cipherHex) {
        if (cipherHex == null || PREVIOUS_KEY == null) return false;
        try {
            byte[] combined = hexToBytes(cipherHex);
            if (combined.length < GCM_IV_LENGTH + 1) return false;
            if (combined[0] == VERSION_CURRENT) {
                decryptWithKey(combined, 1, PREVIOUS_KEY);
            } else {
                decryptWithKey(combined, 0, PREVIOUS_KEY);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runtime key rotation — callable from the admin API without restart.
     * Swaps the current key to a new value, sets the old key as previous,
     * and activates rotation mode. The caller is responsible for triggering
     * the background re-encryption job AND for persisting the new key to
     * app.aes.key (and the old key to app.aes.key.previous) so a restart
     * keeps both readable (Review III C2).
     */
    public static synchronized void rotate(String newKey, String oldKey) {
        SecretKey newCurrent = deriveKey(newKey);
        SecretKey newPrevious = deriveKey(oldKey);
        CURRENT_KEY = newCurrent;
        PREVIOUS_KEY = newPrevious;
        rotationActive = true;
        currentRawKey = newKey;
        previousRawKey = oldKey;
        log.info("AES key rotated at runtime — current=v1, previous=v0. "
                + "IMPORTANT: update AES_KEY (new key) and AES_KEY_PREVIOUS (old key) in env before restart.");
        recordRuntimeRotation(newKey);
    }

    private static void recordRuntimeRotation(String newKey) {
        if (keyAuditRepo == null) return;
        try {
            KeyAudit rotation = new KeyAudit();
            rotation.setEventType("KEY_ROTATION");
            rotation.setKeyVersion("v1+v0");
            rotation.setDetail("Runtime rotation. Update AES_KEY/AES_KEY_PREVIOUS in env before restart.");
            keyAuditRepo.save(rotation);

            // The fingerprint gets its own row so the restart check can compare a
            // value instead of parsing the human-readable detail above (it used to
            // substring/indexOf that sentence, so editing the wording broke it).
            KeyAudit fingerprint = new KeyAudit();
            fingerprint.setEventType(FINGERPRINT_EVENT);
            fingerprint.setKeyVersion("v1+v0");
            fingerprint.setDetail(AesCryptoUtil.fingerprint(newKey));
            keyAuditRepo.save(fingerprint);
        } catch (Exception e) {
            log.error("Failed to record key rotation audit", e);
        }
    }

    /** Short SHA-256 fingerprint of a raw key, for config-consistency checks. */
    public static String fingerprint(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Call after background re-encryption completes. Marks rotation as
     * finished so that future restarts (where yaml still references a
     * previous key) only trigger an empty idempotent scan.
     */
    public static synchronized void markRotationComplete() {
        if (rotationActive) {
            rotationActive = false;
            log.info("AES key rotation marked complete");
        }
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
