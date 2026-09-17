package com.example.medical.security;

import com.example.medical.common.security.AccountRevocationCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtClaimMapper implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final AccountRevocationCheck accountRevocationCheck;

    @Override
    @SuppressWarnings("unchecked")
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        // Refresh tokens are signed with the same local key as access tokens —
        // reject any token that carries the "refresh" scope as an access token
        // (Review III C6).
        List<String> refreshCheck = jwt.getClaimAsStringList("scp");
        if (refreshCheck == null) refreshCheck = jwt.getClaimAsStringList("scope");
        if (refreshCheck != null && refreshCheck.contains("refresh")) {
            throw reject("Refresh tokens are not valid access tokens");
        }

        String username = jwt.getClaimAsString("sub");

        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null) {
            groups = jwt.getClaimAsStringList("roles");
        }
        if (groups == null) groups = List.of();

        Long userId = extractUserId(jwt);
        // Patient tokens carry a patient-table id in uid — never check it against
        // sys_user force-logout, ids overlap between the two tables (R2-1).
        boolean isPatient = groups.stream().anyMatch("PATIENT"::equalsIgnoreCase);
        if (userId != null && userId > 0 && !isPatient) {
            LocalDateTime forceLogout = accountRevocationCheck.forceLogoutAfter(userId);
            if (forceLogout != null && jwt.getIssuedAt() != null) {
                LocalDateTime issuedAt = LocalDateTime.ofInstant(jwt.getIssuedAt(), ZoneId.systemDefault());
                if (issuedAt.isBefore(forceLogout)) {
                    throw reject("Account was disabled or credentials changed after token issuance");
                }
            }
        }
        List<String> scopes = jwt.getClaimAsStringList("scp");
        List<String> perms = jwt.getClaimAsStringList("perm");
        if (scopes == null) scopes = List.of();
        if (perms == null) perms = List.of();

        String scope = jwt.getClaimAsString("scope");
        // The claim arrives as a Number from our own tokens; tolerate a numeric
        // string rather than letting a type mismatch throw and be swallowed.
        Long emergencyPatientId = toLong(jwt.getClaim("patientId"));

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String group : groups) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()));
        }
        for (String s : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
        }
        for (String perm : perms) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        LoginUser loginUser = new LoginUser(userId, username, "", scopes, emergencyPatientId, scope);
        return new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
    }

    /**
     * Rejects a token as an authentication failure rather than a bare JWT error.
     * {@code JwtValidationException} escapes {@code BearerTokenAuthenticationFilter}
     * (which only handles {@code AuthenticationException}), so the container
     * logged an ERROR stack trace on every request from a revoked client and the
     * {@code WWW-Authenticate} header lost its reason. An
     * {@code OAuth2AuthenticationException} is caught by the filter and answered
     * properly: 401 with the description below.
     */
    private static OAuth2AuthenticationException reject(String reason) {
        return new OAuth2AuthenticationException(
                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", reason, null));
    }

    private static Long toLong(Object claim) {
        if (claim instanceof Number n) return n.longValue();
        if (claim instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long extractUserId(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid != null) {
            try {
                return Long.valueOf(uid);
            } catch (NumberFormatException ignored) {
            }
        }
        String jti = jwt.getId();
        if (jti != null) {
            try {
                return Long.valueOf(jti);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}
