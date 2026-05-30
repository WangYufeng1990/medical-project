package com.example.medical.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DevJwtEncoder implements JwtEncoder {

    private final byte[] secret;

    public DevJwtEncoder(byte[] secret) {
        this.secret = secret;
    }

    @Override
    public Jwt encode(JwtEncoderParameters parameters) {
        var src = parameters.getClaims();
        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(src.getSubject())
                .issueTime(Date.from(src.getIssuedAt()))
                .expirationTime(Date.from(src.getExpiresAt()));
        if (src.getId() != null) {
            claimsBuilder.jwtID(src.getId());
        }
        src.getClaims().forEach((key, value) -> {
            if (!"sub".equals(key) && !"iat".equals(key)
                    && !"exp".equals(key) && !"jti".equals(key)) {
                claimsBuilder.claim(key, value);
            }
        });

        try {
            JWSSigner signer = new MACSigner(secret);
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsBuilder.build());
            signedJWT.sign(signer);
            String tokenValue = signedJWT.serialize();

            Map<String, Object> allClaims = new HashMap<>(src.getClaims());
            allClaims.put("sub", src.getSubject());
            allClaims.put("iat", src.getIssuedAt().getEpochSecond());
            allClaims.put("exp", src.getExpiresAt().getEpochSecond());
            if (src.getId() != null) {
                allClaims.put("jti", src.getId());
            }

            return new Jwt(tokenValue, src.getIssuedAt(), src.getExpiresAt(),
                    Map.of("alg", "HS256"), allClaims);
        } catch (JOSEException e) {
            throw new org.springframework.security.oauth2.jwt.JwtEncodingException(
                    "Failed to sign dev JWT", e);
        }
    }
}
