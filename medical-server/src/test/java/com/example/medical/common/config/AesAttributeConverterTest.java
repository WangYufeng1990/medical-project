package com.example.medical.common.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesAttributeConverterTest {

    @BeforeAll
    static void setUp() {
        AesCryptoUtil.initializeForTest("test-aes-key-for-unit-tests-32bytes!");
    }

    private final AesAttributeConverter converter = new AesAttributeConverter();

    @Test
    void shouldEncryptAndDecryptRoundtrip() {
        String plaintext = "123-45-6789";
        String encrypted = converter.convertToDatabaseColumn(plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String plaintext = "312-555-0101";
        String enc1 = converter.convertToDatabaseColumn(plaintext);
        String enc2 = converter.convertToDatabaseColumn(plaintext);
        assertNotEquals(enc1, enc2, "GCM should produce different ciphertext due to random IV");
    }

    @Test
    void shouldReturnPlaceholderOnDecryptFailure() {
        String result = AesCryptoUtil.decrypt("not-valid-hex-data");
        assertEquals("[DECRYPT_FAILED]", result,
                "Corrupt data should return placeholder, not throw");
    }

    // ── reencrypt ──

    @Test
    void reencrypt_shouldReturnNewCiphertext() {
        String plaintext = "test-data";
        String encrypted = AesCryptoUtil.encrypt(plaintext);
        String reencrypted = AesCryptoUtil.reencrypt(encrypted);

        assertNotNull(reencrypted);
        // re-encrypted ciphertext should still decrypt to the original plaintext
        assertEquals(plaintext, AesCryptoUtil.decrypt(reencrypted));
    }

    @Test
    void reencrypt_shouldReturnNullForNullInput() {
        assertNull(AesCryptoUtil.reencrypt(null));
    }

    @Test
    void reencrypt_shouldReturnNullForCorruptInput() {
        assertNull(AesCryptoUtil.reencrypt("not-valid-hex-data"));
    }

    @Test
    void reencrypt_shouldUpgradeLegacyCiphertextToV1() throws Exception {
        // Create legacy-format ciphertext (unversioned) encrypted with old-key
        String oldKeyRaw = "old-key-for-unit-tests-32bytes!!";
        String newKeyRaw = "new-key-for-unit-tests-32bytes!!";
        String plaintext = "legacy-data";

        // Derive old key the same way AesCryptoUtil does
        var oldKey = deriveTestKey(oldKeyRaw);

        // Encrypt raw: [IV:12B][ciphertext+tag] — no version byte
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        var cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, oldKey,
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(iv.length + ct.length);
        buf.put(iv);
        buf.put(ct);
        String legacyCipher = bytesToHex(buf.array());

        // Activate rotation: new-key as current, old-key as previous
        AesCryptoUtil.initializeForTest(newKeyRaw, oldKeyRaw);
        assertTrue(AesCryptoUtil.isRotationActive());

        // Legacy ciphertext should decrypt correctly with previous key
        assertEquals(plaintext, AesCryptoUtil.decrypt(legacyCipher));

        // Re-encrypt should upgrade to v1
        String reencrypted = AesCryptoUtil.reencrypt(legacyCipher);
        assertNotNull(reencrypted);
        assertEquals(plaintext, AesCryptoUtil.decrypt(reencrypted));
        assertTrue(reencrypted.startsWith("01"),
                "Re-encrypted ciphertext should have v1 prefix");

        // Reset to the test key for other tests
        AesCryptoUtil.initializeForTest("test-aes-key-for-unit-tests-32bytes!");
    }

    @Test
    void rotation_shouldKeepVersionedRowsReadableAndMigratable() {
        // Review III C2: v1 rows were previously unreadable after rotation
        // (decrypt only tried CURRENT_KEY) and the migration predicate
        // excluded them. This test pins the fixed behaviour end-to-end.
        String keyA = "rotation-key-a-for-unit-tests-32bytes!";
        String keyB = "rotation-key-b-for-unit-tests-32bytes!";

        // Phase 1: single key A — write a v1 ciphertext
        AesCryptoUtil.initializeForTest(keyA);
        String oldCipher = AesCryptoUtil.encrypt("hipaa-phrase");
        assertTrue(oldCipher.startsWith("01"));

        // Phase 2: rotate to B (A becomes previous) — old v1 rows must still decrypt
        AesCryptoUtil.rotate(keyB, keyA);
        assertTrue(AesCryptoUtil.isRotationActive());
        assertEquals("hipaa-phrase", AesCryptoUtil.decrypt(oldCipher),
                "v1 row encrypted with the previous key must decrypt via fallback");

        String newCipher = AesCryptoUtil.encrypt("fresh-write");
        assertEquals("fresh-write", AesCryptoUtil.decrypt(newCipher),
                "new writes must decrypt with the current key");

        // Migration targeting: only the old-key row needs re-encryption
        assertTrue(AesCryptoUtil.isEncryptedWithPreviousKey(oldCipher));
        assertFalse(AesCryptoUtil.isEncryptedWithPreviousKey(newCipher));

        // reencrypt() must succeed for old-key v1 rows (previously null → skipped)
        String migrated = AesCryptoUtil.reencrypt(oldCipher);
        assertNotNull(migrated, "reencrypt of a previous-key v1 row must not be null");
        assertEquals("hipaa-phrase", AesCryptoUtil.decrypt(migrated));
        assertFalse(AesCryptoUtil.isEncryptedWithPreviousKey(migrated),
                "after migration the row is encrypted with the current key");

        // Phase 3: simulate restart with updated config (AES_KEY=B, AES_KEY_PREVIOUS=A)
        AesCryptoUtil.initializeForTest(keyB, keyA);
        assertEquals("hipaa-phrase", AesCryptoUtil.decrypt(oldCipher));
        assertEquals("fresh-write", AesCryptoUtil.decrypt(newCipher));

        // Reset to the test key for other tests
        AesCryptoUtil.initializeForTest("test-aes-key-for-unit-tests-32bytes!");
    }

    private static javax.crypto.SecretKey deriveTestKey(String raw) throws Exception {
        var factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] salt = "medical-aes-v2-salt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var spec = new javax.crypto.spec.PBEKeySpec(raw.toCharArray(), salt, 310_000, 256);
        return new javax.crypto.spec.SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
