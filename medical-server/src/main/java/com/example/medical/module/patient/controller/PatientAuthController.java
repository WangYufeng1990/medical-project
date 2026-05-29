package com.example.medical.module.patient.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientAuthController {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final PatientRepository patientRepository;
    private final PatientAuthRepository patientAuthRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${okta.client-id:#{null}}")
    private String clientId;

    @Value("${okta.client-secret:#{null}}")
    private String clientSecret;

    @Value("${okta.issuer-uri:#{null}}")
    private String issuerUri;

    @PostMapping("/login")
    public Result<PatientLoginResponse> login(@Valid @RequestBody PatientLoginRequest request) {
        PatientAuth auth = patientAuthRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password"));
        if (auth.getStatus() != null && auth.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }
        if (isLocked(auth)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Account is temporarily locked. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            recordFailedAttempt(auth);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }

        resetFailedAttempts(auth);

        Patient patient = patientRepository.findById(auth.getPatientId())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Patient record not found"));

        TokenPair tokens = exchangeForTokens(request.getUsername(), request.getPassword());

        return Result.ok(new PatientLoginResponse(tokens.accessToken(), tokens.refreshToken(),
                patient.getId(), patient.getName(), request.getUsername()));
    }

    @PostMapping("/refresh")
    public Result<PatientLoginResponse> refresh(@Valid @RequestBody PatientRefreshRequest request) {
        TokenPair tokens = callOktaRefreshEndpoint(request.getRefreshToken());

        return Result.ok(new PatientLoginResponse(tokens.accessToken(), tokens.refreshToken(),
                null, null, null));
    }

    private TokenPair exchangeForTokens(String username, String password) {
        if (clientId == null || issuerUri == null) {
            return new TokenPair("dev-token-" + username, null);
        }
        return callOktaPasswordGrant(username, password);
    }

    private TokenPair callOktaPasswordGrant(String username, String password) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("username", username);
        body.add("password", password);
        body.add("scope", "openid profile");

        ResponseEntity<Map> response = rt.exchange(
                issuerUri + "/v1/token", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Okta authentication failed");
        }
        Map<String, Object> res = response.getBody();
        return new TokenPair(
                (String) res.get("access_token"),
                (String) res.get("refresh_token"));
    }

    private TokenPair callOktaRefreshEndpoint(String refreshToken) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        ResponseEntity<Map> response = rt.exchange(
                issuerUri + "/v1/token", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token refresh failed");
        }
        Map<String, Object> res = response.getBody();
        return new TokenPair(
                (String) res.get("access_token"),
                (String) res.get("refresh_token"));
    }

    private void recordFailedAttempt(PatientAuth auth) {
        int attempts = auth.getFailedAttempts() != null ? auth.getFailedAttempts() + 1 : 1;
        auth.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            auth.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        patientAuthRepository.save(auth);
    }

    private void resetFailedAttempts(PatientAuth auth) {
        auth.setFailedAttempts(0);
        auth.setLockedUntil(null);
        auth.setLastLoginTime(LocalDateTime.now());
        patientAuthRepository.save(auth);
    }

    private boolean isLocked(PatientAuth auth) {
        return auth.getLockedUntil() != null && auth.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private record TokenPair(String accessToken, String refreshToken) {}

    @Data
    static class PatientLoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    static class PatientLoginResponse {
        private final String token;
        private final String refreshToken;
        private final Long patientId;
        private final String name;
        private final String username;
    }

    @Data
    static class PatientRefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }
}
