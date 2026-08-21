package com.example.medical.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production token trust model (Review III C6).
 * <p>
 * Staff tokens are validated against the external IdP (Okta) JWKS via
 * {@code okta.issuer-uri}. Patient / emergency / refresh tokens are issued
 * locally (the patient portal has no external IdP) and are validated against
 * an independent {@code JWT_SIGNING_KEY} — never a fallback to AES_KEY.
 * {@code JWT_SIGNING_KEY} must be ≥ 32 chars and distinct from AES_KEY
 * (enforced here and by {@link ProdGuard}).
 */
@Slf4j
@Configuration
@Profile("prod")
public class SecurityConfigProd {

    private final byte[] signingKey;

    public SecurityConfigProd(@Value("${JWT_SIGNING_KEY:}") String jwtSigningKey) {
        if (jwtSigningKey == null || jwtSigningKey.isBlank() || jwtSigningKey.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY env var is required for production (min 32 chars) and must be "
                    + "independent from AES_KEY (key separation, Review III C6).");
        }
        this.signingKey = derive256BitKey(jwtSigningKey);
        log.info("Production JWT signing key initialized (independent of AES key)");
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${okta.issuer-uri:}") String issuerUri) {
        JwtDecoder oktaDecoder = null;
        if (issuerUri != null && !issuerUri.isBlank()) {
            oktaDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        }
        JwtDecoder localDecoder = localDecoder();
        if (oktaDecoder == null) {
            log.warn("okta.issuer-uri not configured — only locally-signed patient/emergency tokens will validate");
            return localDecoder;
        }
        return new CompositeJwtDecoder(oktaDecoder, localDecoder);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new com.example.medical.security.DevJwtEncoder(signingKey);
    }

    private JwtDecoder localDecoder() {
        SecretKeySpec key = new SecretKeySpec(signingKey, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer("https://medical-server"),
                        JwtValidators.createDefaultWithIssuer("https://medical-server/patient"),
                        JwtValidators.createDefaultWithIssuer("https://medical-server/patient/refresh"))));
        return decoder;
    }

    private static byte[] derive256BitKey(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
