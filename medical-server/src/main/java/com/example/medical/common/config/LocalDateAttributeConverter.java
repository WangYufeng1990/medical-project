package com.example.medical.common.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter
public class LocalDateAttributeConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate value) {
        if (value == null) return null;
        return AesCryptoUtil.encrypt(value.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbValue) {
        if (dbValue == null) return null;
        String plain = AesCryptoUtil.decrypt(dbValue);
        if (plain == null || plain.equals("[DECRYPT_FAILED]")) return null;
        try {
            return LocalDate.parse(plain);
        } catch (Exception e) {
            return null;
        }
    }
}
