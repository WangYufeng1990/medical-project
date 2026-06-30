package com.example.medical.security;

import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtClaimMapper implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    private final SysUserRepository sysUserRepository;

    @Override
    @SuppressWarnings("unchecked")
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        Long userId = extractUserId(jwt);
        if (userId != null && userId > 0) {
            LocalDateTime forceLogout = sysUserRepository.findForceLogoutAfterByUserId(userId);
            if (forceLogout != null && jwt.getIssuedAt() != null) {
                LocalDateTime issuedAt = LocalDateTime.ofInstant(jwt.getIssuedAt(), ZoneId.systemDefault());
                if (issuedAt.isBefore(forceLogout)) {
                    throw new JwtValidationException("Token issued before force logout",
                            List.of(new org.springframework.security.oauth2.core.OAuth2Error(
                                    "token_revoked", "Account was disabled or credentials changed after token issuance", null)));
                }
            }
        }

        String username = jwt.getClaimAsString("sub");

        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null) {
            groups = jwt.getClaimAsStringList("roles");
        }
        List<String> scopes = jwt.getClaimAsStringList("scp");
        List<String> perms = jwt.getClaimAsStringList("perm");
        if (groups == null) groups = List.of();
        if (scopes == null) scopes = List.of();
        if (perms == null) perms = List.of();

        String scope = jwt.getClaimAsString("scope");
        Long emergencyPatientId = null;
        try {
            Number n = jwt.getClaim("patientId");
            if (n != null) emergencyPatientId = n.longValue();
        } catch (Exception ignored) {}

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
