package com.example.medical.module.system.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.LoginRequest;
import com.example.medical.module.system.dto.LoginResponse;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final SysUserRepository sysUserRepository;
    private final LockoutService lockoutService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final com.example.medical.common.audit.AuditLogWriter auditLogWriter;
    private final jakarta.servlet.http.HttpServletRequest request;
    private final org.springframework.web.client.RestTemplate oktaRestTemplate;

    @Value("${okta.client-id:}")
    private String clientId;

    @Value("${okta.client-secret:}")
    private String clientSecret;

    @Value("${okta.issuer-uri:}")
    private String issuerUri;

    @Value("${app.security.access-token-expiry-seconds:7200}")
    private long accessTokenExpirySeconds;

    @Value("${app.security.dev-mode:false}")
    private boolean devMode;

    @Transactional
    @SuppressWarnings("unchecked")
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    auditLoginFailure(null, request.getUsername(), "USER_NOT_FOUND");
                    return new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
                });
        if (user.getStatus() != null && user.getStatus() == 0) {
            auditLoginFailure(user.getId(), user.getUsername(), "ACCOUNT_DISABLED");
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }
        if (isLocked(user)) {
            auditLoginFailure(user.getId(), user.getUsername(), "ACCOUNT_LOCKED");
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Account is temporarily locked. Try again later.");
        }

        List<String> roles = sysUserRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserRepository.findPermissionsByUserId(user.getId());

        TokenPair tokens;
        if (devMode) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                recordFailedAttempt(user);
                auditLoginFailure(user.getId(), user.getUsername(), "BAD_CREDENTIALS");
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
            }
            resetFailedAttempts(user);
            tokens = generateDevToken(user.getId(), user.getUsername(), roles, permissions);
        } else {
            tokens = callOktaTokenEndpoint(request.getUsername(), request.getPassword());
            if (tokens == null) {
                recordFailedAttempt(user);
                auditLoginFailure(user.getId(), user.getUsername(), "OKTA_AUTH_FAILED");
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
            }
            resetFailedAttempts(user);
        }

        user.setLastLoginTime(LocalDateTime.now());

        return LoginResponse.fromEntity(user, roles, permissions,
                tokens.accessToken(), tokens.refreshToken());
    }

    private void auditLoginFailure(Long userId, String username, String reason) {
        try {
            auditLogWriter.writeAsync(userId, username, null,
                    "auth", "LOGIN_FAILED", username,
                    "reason=" + reason,
                    request != null ? request.getRemoteAddr() : "unknown",
                    java.time.Instant.now());
        } catch (Exception ignored) {
        }
    }

    public LoginResponse refresh(String refreshToken) {
        if (isBlank(clientId) || isBlank(issuerUri)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "Token refresh not available in dev mode — log in again");
        }
        Map<String, Object> tokenResponse = callOktaRefreshEndpoint(refreshToken);

        String accessToken = (String) tokenResponse.get("access_token");
        String newRefreshToken = (String) tokenResponse.get("refresh_token");

        RestTemplate restTemplate = oktaRestTemplate;
        String userinfoUrl = issuerUri + "/v1/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> userInfoResp = restTemplate.exchange(
                userinfoUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> userInfo = userInfoResp.getBody();
        String username = userInfo != null ? (String) userInfo.getOrDefault("sub", "unknown") : "unknown";
        Long userId = userInfo != null ? resolveUserId(userInfo) : 0L;

        List<String> roles = List.of();
        List<String> permissions = List.of();
        if (userId > 0) {
            SysUser user = sysUserRepository.findById(userId).orElse(null);
            if (user != null) {
                roles = sysUserRepository.findRoleCodesByUserId(userId);
                permissions = sysUserRepository.findPermissionsByUserId(userId);
            }
        }

        return LoginResponse.forRefresh(accessToken,
                newRefreshToken != null ? newRefreshToken : refreshToken,
                userId, username, roles, permissions);
    }

    private TokenPair generateDevToken(Long userId, String username, List<String> roles,
                                         List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .id(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpirySeconds))
                .claim("uid", userId.toString())
                .claim("roles", roles)
                .claim("scp", buildFhirScopes(roles))
                .claim("perm", permissions != null ? permissions : List.of())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenPair(token, null);
    }

    private TokenPair callOktaTokenEndpoint(String username, String password) {
        try {
            RestTemplate restTemplate = oktaRestTemplate;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("username", username);
            body.add("password", password);
            body.add("scope", "openid profile email groups");

            ResponseEntity<Map> response = restTemplate.exchange(
                    issuerUri + "/v1/token",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            Map<String, Object> res = response.getBody();
            return new TokenPair(
                    (String) res.get("access_token"),
                    (String) res.get("refresh_token"));
        } catch (Exception e) {
            log.warn("Okta token endpoint call failed for user={}: {}", username, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> callOktaRefreshEndpoint(String refreshToken) {
        RestTemplate restTemplate = oktaRestTemplate;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);
        body.add("scope", "openid profile email groups");

        ResponseEntity<Map> response = restTemplate.exchange(
                issuerUri + "/v1/token",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token refresh failed");
        }
        return response.getBody();
    }

    private Long resolveUserId(Map<String, Object> userInfo) {
        Object uid = userInfo.get("uid");
        if (uid instanceof Number n) return n.longValue();
        if (uid instanceof String s) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private void recordFailedAttempt(SysUser user) {
        lockoutService.recordFailedAttempt(user.getId());
    }

    private void resetFailedAttempts(SysUser user) {
        lockoutService.resetFailedAttempts(user.getId());
    }

    private boolean isLocked(SysUser user) {
        boolean exceeded = user.getFailedAttempts() != null && user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS;
        boolean timeLocked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
        return exceeded && timeLocked;
    }

    private static List<String> buildFhirScopes(List<String> roles) {
        List<String> scopes = new java.util.ArrayList<>(List.of("openid", "profile", "email"));
        if (roles != null) {
            if (roles.contains("ADMIN") || roles.contains("DOCTOR")) {
                scopes.add("patient/*.read");
                scopes.add("patient/*.write");
                scopes.add("user/*.read");
                scopes.add("system/*.read");
            }
            if (roles.contains("PATIENT")) {
                scopes.add("patient/Patient.read");
                scopes.add("patient/Observation.read");
            }
        }
        return scopes;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record TokenPair(String accessToken, String refreshToken) {}
}
