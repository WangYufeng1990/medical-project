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
        String username = jwt.getSubject();
        Long userId = Long.valueOf(jwt.getId());

        List<String> roles = jwt.getClaimAsStringList("roles");
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (roles == null) roles = List.of();
        if (permissions == null) permissions = List.of();

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        for (String perm : permissions) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }

        LoginUser loginUser = new LoginUser(userId, username, "", permissions);
        return new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
    }
}
