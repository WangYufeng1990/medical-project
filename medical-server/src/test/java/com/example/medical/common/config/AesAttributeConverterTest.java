package com.example.medical.common.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesAttributeConverterTest {

    @BeforeAll
    static void setUp() {
        System.setProperty("aes.key", "test-aes-key-for-unit-tests-32bytes!");
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
}
