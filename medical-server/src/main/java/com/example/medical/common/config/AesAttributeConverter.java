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

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return AesCryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return AesCryptoUtil.decrypt(dbData);
    }
}
