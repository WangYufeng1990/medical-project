package com.example.medical.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class LoginUser implements UserDetails {

    private Long userId;
    private String username;
    private String password;
    private List<String> permissions;
    private Long emergencyPatientId;
    private String scope;

    public LoginUser(Long userId, String username, String password, List<String> permissions) {
        this(userId, username, password, permissions, null, null);
    }

    public LoginUser(Long userId, String username, String password, List<String> permissions,
                     Long emergencyPatientId, String scope) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.permissions = permissions;
        this.emergencyPatientId = emergencyPatientId;
        this.scope = scope;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null) return java.util.Collections.emptyList();
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
