package com.example.medical.common.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The production profile's local token contract (R53-04).
 * <p>
 * {@code SecurityConfigProd.localDecoder()} combined three
 * {@code createDefaultWithIssuer(...)} validators inside one
 * {@code DelegatingOAuth2TokenValidator}, and such a validator requires
 * <em>every</em> delegate to pass — so no locally-signed token could ever be
 * decoded, whichever issuer it carried. No test covered this profile, which is how
 * a decoder that rejected all of its own tokens stayed green in CI.
 * <p>
 * What is asserted here: the encoder is the single source of the issuer (it derives
 * it from the roles), the decoder accepts exactly that, and it still checks
 * signature and expiry. No Spring context — the class under test takes the signing
 * key as a constructor argument.
 */
class SecurityConfigProdTest {

    private static final String SIGNING_KEY = "test-signing-key-for-the-prod-local-contract";

    private final SecurityConfigProd config = new SecurityConfigProd(SIGNING_KEY);
    private final JwtEncoder encoder = config.jwtEncoder();

    @Test
    void locallySignedStaffToken_shouldDecode() {
        Jwt jwt = decode(encode(claims("admin", List.of("ADMIN", "DOCTOR"), List.of("openid"))));

        assertEquals("https://medical-server", jwt.getIssuer().toString());
        assertEquals("admin", jwt.getSubject());
    }

    /** The finding: a patient token could not be decoded at all. */
    @Test
    void locallySignedPatientToken_shouldDecode() {
        Jwt jwt = decode(encode(patientClaims(List.of("patient/*.read"))));

        assertEquals("https://medical-server/patient", jwt.getIssuer().toString());
        assertEquals("100", jwt.getClaim("uid").toString());
    }

    @Test
    void locallySignedRefreshToken_shouldDecode() {
        Jwt jwt = decode(encode(patientClaims(List.of("refresh"))));

        assertTrue(jwt.getClaimAsStringList("scp").contains("refresh"),
                "a refresh token is distinguishable by its scope, not by a third issuer");
    }

    @Test
    void locallySignedEmergencyToken_shouldDecode() {
        Jwt jwt = decode(encode(JwtClaimsSet.builder()
                .subject("doctor1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of("DOCTOR"))
                .claim("scp", List.of("EMERGENCY"))
                .claim("patientId", 100)
                .build()));

        assertEquals("https://medical-server", jwt.getIssuer().toString());
        assertEquals(100, ((Number) jwt.getClaim("patientId")).intValue());
    }

    /**
     * Signed with the very key the decoder holds — a valid signature, and the token
     * must still be refused because its issuer is not one this server signs with.
     */
    @Test
    void tokenWithAnUntrustedIssuer_shouldBeRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("admin")
                        .issuer("https://evil.example.com")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                        .claim("roles", List.of("ADMIN"))
                        .build());
        jwt.sign(new MACSigner(deriveKey(SIGNING_KEY)));

        JwtException failure = assertThrows(JwtException.class, () -> decode(jwt.serialize()));
        assertTrue(failure.getMessage().contains("iss"), "the failure should name the issuer claim");
    }

    @Test
    void tokenSignedWithAnotherKey_shouldBeRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("admin")
                        .issuer("https://medical-server")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                        .claim("roles", List.of("ADMIN"))
                        .build());
        jwt.sign(new MACSigner(deriveKey("some-other-signing-key-of-sufficient-length")));

        JwtException failure = assertThrows(JwtException.class, () -> decode(jwt.serialize()));
        assertTrue(failure.getMessage().toLowerCase().contains("signature"),
                "the signature check must be what refuses it: " + failure.getMessage());
    }

    @Test
    void expiredToken_shouldBeRejected() {
        JwtClaimsSet expired = JwtClaimsSet.builder()
                .subject("admin")
                .issuedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .claim("roles", List.of("ADMIN"))
                .build();

        JwtException failure = assertThrows(JwtException.class, () -> decode(encode(expired)));
        assertTrue(failure.getMessage().toLowerCase().contains("expired"),
                "the timestamp check must be what refuses it: " + failure.getMessage());
    }

    // ──────────────────────────────────────────────────────

    private JwtClaimsSet patientClaims(List<String> scopes) {
        return JwtClaimsSet.builder()
                .subject("patient1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .claim("roles", List.of("PATIENT"))
                .claim("scp", scopes)
                .claim("uid", "100")
                .build();
    }

    private JwtClaimsSet claims(String subject, List<String> roles, List<String> scopes) {
        return JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .claim("roles", roles)
                .claim("scp", scopes)
                .build();
    }

    private String encode(JwtClaimsSet claimsSet) {
        return encoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue();
    }

    /** SHA-256 of the configured key — the derivation {@code SecurityConfigProd} performs. */
    private static byte[] deriveKey(String raw) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** No Okta issuer configured, which is the state the finding was measured in. */
    private Jwt decode(String token) {
        JwtDecoder decoder = config.jwtDecoder("");
        return decoder.decode(token);
    }
}
