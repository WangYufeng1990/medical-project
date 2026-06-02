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
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

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

    @SuppressWarnings("unchecked")
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }
        if (isLocked(user)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Account is temporarily locked. Try again later.");
        }

        List<String> roles = sysUserRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserRepository.findPermissionsByUserId(user.getId());

        TokenPair tokens;
        if (devMode) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                recordFailedAttempt(user);
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
            }
            resetFailedAttempts(user);
            tokens = generateDevToken(user.getId(), user.getUsername(), roles);
        } else {
            tokens = callOktaTokenEndpoint(request.getUsername(), request.getPassword());
            if (tokens == null) {
                recordFailedAttempt(user);
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
            }
            resetFailedAttempts(user);
        }

        return LoginResponse.fromEntity(user, roles, permissions,
                tokens.accessToken(), tokens.refreshToken());
    }

    public LoginResponse refresh(String refreshToken) {
        if (isBlank(clientId) || isBlank(issuerUri)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "Token refresh not available in dev mode — log in again");
        }
        Map<String, Object> tokenResponse = callOktaRefreshEndpoint(refreshToken);

        String accessToken = (String) tokenResponse.get("access_token");
        String newRefreshToken = (String) tokenResponse.get("refresh_token");

        RestTemplate restTemplate = new RestTemplate();
        String userinfoUrl = issuerUri + "/v1/userinfo";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> userInfoResp = restTemplate.exchange(
                userinfoUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> userInfo = userInfoResp.getBody();
        String username = userInfo != null ? (String) userInfo.getOrDefault("sub", "unknown") : "unknown";
        Long userId = userInfo != null ? resolveUserId(userInfo) : 0L;

        return LoginResponse.forRefresh(accessToken,
                newRefreshToken != null ? newRefreshToken : refreshToken,
                userId, username, List.of(), List.of());
    }

    private TokenPair generateDevToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .id(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTokenExpirySeconds))
                .claim("uid", userId.toString())
                .claim("roles", roles)
                .claim("scp", buildFhirScopes(roles))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenPair(token, null);
    }

    private TokenPair callOktaTokenEndpoint(String username, String password) {
        try {
            RestTemplate restTemplate = new RestTemplate();

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
        RestTemplate restTemplate = new RestTemplate();

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
        int attempts = user.getFailedAttempts() != null ? user.getFailedAttempts() + 1 : 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        sysUserRepository.save(user);
    }

    private void resetFailedAttempts(SysUser user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        sysUserRepository.save(user);
    }

    private boolean isLocked(SysUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
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
