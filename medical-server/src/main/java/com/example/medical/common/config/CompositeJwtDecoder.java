package com.example.medical.common.config;

import com.example.medical.security.DevJwtEncoder;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.text.ParseException;
import java.util.Set;

/**
 * Routes JWT validation by issuer (Review III C6):
 * <ul>
 *   <li>locally-signed tokens (staff dev / patient / emergency / refresh) →
 *       the local HS256 decoder with strict issuer validation;</li>
 *   <li>everything else (Okta-issued staff tokens) → the IdP JWKS decoder,
 *       falling back to the local decoder if the issuer lookup failed.</li>
 * </ul>
 * Not depending on {@code DelegatingJwtDecoder} (not present in the resolved
 * Spring Security 6.4 jars) — the routing is explicit and testable.
 */
public class CompositeJwtDecoder implements JwtDecoder {

    /**
     * The issuers {@link com.example.medical.security.DevJwtEncoder} can emit, and
     * therefore the set the local decoder trusts. A third value
     * ({@code .../patient/refresh}) used to be listed here: it came from the refresh
     * builder's {@code .issuer(...)}, which the encoder overwrote, so no token ever
     * carried it (R53-04).
     */
    static final Set<String> LOCAL_ISSUERS = Set.of(
            DevJwtEncoder.STAFF_ISSUER_URI,
            DevJwtEncoder.PATIENT_ISSUER_URI);

    private final JwtDecoder oktaDecoder;
    private final JwtDecoder localDecoder;

    public CompositeJwtDecoder(JwtDecoder oktaDecoder, JwtDecoder localDecoder) {
        this.oktaDecoder = oktaDecoder;
        this.localDecoder = localDecoder;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuer(token);
        if (issuer != null && LOCAL_ISSUERS.contains(issuer)) {
            return localDecoder.decode(token);
        }
        if (oktaDecoder != null) {
            try {
                return oktaDecoder.decode(token);
            } catch (JwtException oktaFailure) {
                // Not an Okta token (or issuer unreachable) — try local.
                return localDecoder.decode(token);
            }
        }
        return localDecoder.decode(token);
    }

    private static String extractIssuer(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            return null;
        }
    }
}
