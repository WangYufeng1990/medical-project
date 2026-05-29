package com.example.medical.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JwtClaimMapper implements Converter<Jwt, UsernamePasswordAuthenticationToken> {

    @Override
    @SuppressWarnings("unchecked")
    public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getClaimAsString("sub");

        Long userId = extractUserId(jwt);

        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null) {
            groups = jwt.getClaimAsStringList("roles");
        }
        List<String> scopes = jwt.getClaimAsStringList("scp");
        if (groups == null) groups = List.of();
        if (scopes == null) scopes = List.of();

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String group : groups) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()));
        }
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }

        LoginUser loginUser = new LoginUser(userId, username, "", scopes);
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
