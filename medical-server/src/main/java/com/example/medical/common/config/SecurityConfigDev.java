package com.example.medical.common.config;

import com.example.medical.security.DevJwtEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Configuration
@Profile({"dev", "h2"})
public class SecurityConfigDev {

    @org.springframework.beans.factory.annotation.Value("${app.security.dev-jwt-secret:}")
    private String configuredKey;

    private String getRawKey() {
        if (configuredKey != null && !configuredKey.isBlank()) {
            return configuredKey;
        }
        // No hardcoded fallback (Review III C5) — dev/h2 must opt in explicitly.
        throw new IllegalStateException(
                "app.security.dev-jwt-secret is required when the dev/h2 profile is active "
                + "(Review III C5: no hardcoded fallback key).");
    }

    private byte[] derive256BitKey() {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(getRawKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        byte[] keyBytes = derive256BitKey();
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new DevJwtEncoder(derive256BitKey());
    }
}
