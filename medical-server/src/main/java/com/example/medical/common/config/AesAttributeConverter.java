package com.example.medical.common.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} for transparent PHI field encryption.
 * <p>
 * Delegates to {@link AesCryptoUtil} (AES-256-GCM) so encryption is
 * transparent to entity fields and business logic. JPA instantiates this
 * converter directly (not via Spring), so the crypto utility uses a
 * static method bridge initialized by Spring's {@code @PostConstruct}.
 * <p>
 * Decryption failures return {@code [DECRYPT_FAILED]} rather than
 * throwing — a single corrupt row will not crash an entire JPA query.
 */
@Converter
public class AesAttributeConverter implements AttributeConverter<String, String> {

    /** Literal placeholder returned by {@link AesCryptoUtil#decrypt} on failure. */
    private static final String DECRYPT_FAILED = "[DECRYPT_FAILED]";

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // Review III M5: never re-encrypt the failure placeholder back over
        // real ciphertext — that would permanently destroy the PHI.
        if (attribute != null && attribute.startsWith(DECRYPT_FAILED)) {
            throw new IllegalStateException(
                    "Refusing to persist a decrypt-failure placeholder (" + DECRYPT_FAILED
                    + ") — the source ciphertext is unreadable. Investigate key rotation/corruption before writing.");
        }
        return AesCryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return AesCryptoUtil.decrypt(dbData);
    }
}
