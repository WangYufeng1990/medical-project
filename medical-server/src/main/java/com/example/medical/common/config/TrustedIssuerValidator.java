package com.example.medical.common.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Set;

/**
 * Accepts any issuer in the trusted set — one validator, not one per issuer.
 * <p>
 * {@code SecurityConfigProd} used to build its local decoder from three
 * {@code JwtValidators.createDefaultWithIssuer(...)} validators inside a single
 * {@link org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator}.
 * A delegating validator requires every delegate to pass, and each issuer validator
 * rejects every other issuer, so no locally-signed token could be decoded at all —
 * the decoder refused the very tokens this server signs (R53-04).
 */
public class TrustedIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private final Set<String> trustedIssuers;

    public TrustedIssuerValidator(Set<String> trustedIssuers) {
        this.trustedIssuers = Set.copyOf(trustedIssuers);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String issuer = token.getIssuer() == null ? null : token.getIssuer().toString();
        if (issuer != null && trustedIssuers.contains(issuer)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                "The iss claim is not valid: " + issuer + " is not one of " + trustedIssuers, null));
    }
}
