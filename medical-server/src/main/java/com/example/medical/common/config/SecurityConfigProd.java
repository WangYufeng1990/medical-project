package com.example.medical.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Configuration
@Profile("prod")
public class SecurityConfigProd {

    private final String rawKey;

    public SecurityConfigProd(@org.springframework.beans.factory.annotation.Value("${AES_KEY}") String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() < 16) {
            throw new IllegalStateException("AES_KEY env var is required for production JWT signing (min 16 chars, found " +
                    (rawKey == null ? "null" : String.valueOf(rawKey.length())) + ")");
        }
        this.rawKey = rawKey;
        log.info("Production JWT signing key initialized from AES_KEY ({} chars)", rawKey.length());
    }

    private byte[] derive256BitKey() {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(derive256BitKey(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new com.example.medical.security.DevJwtEncoder(derive256BitKey());
    }
}
