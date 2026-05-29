package com.example.medical.module.patient.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.security.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientAuthController {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final PatientRepository patientRepository;
    private final PatientAuthRepository patientAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedissonClient redissonClient;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @PostMapping("/login")
    public Result<PatientLoginResponse> login(@Valid @RequestBody PatientLoginRequest request) {
        PatientAuth auth = patientAuthRepository.findByUsername(request.getUsername())
                .orElse(null);
        if (auth == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (auth.getStatus() != null && auth.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }
        if (isLocked(auth)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Account is temporarily locked due to too many failed attempts. Try again later.");
        }

        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            recordFailedAttempt(auth);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }

        resetFailedAttempts(auth);

        Patient patient = patientRepository.findById(auth.getPatientId())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Patient record not found"));

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("PATIENT"));
        claims.put("patientId", patient.getId());

        String token = jwtUtils.generateToken(patient.getId(), auth.getUsername(), claims);
        String refreshToken = generateRefreshToken(patient.getId());

        return Result.ok(new PatientLoginResponse(token, refreshToken,
                patient.getId(), patient.getName(), auth.getUsername()));
    }

    @PostMapping("/refresh")
    public Result<PatientLoginResponse> refresh(@Valid @RequestBody PatientRefreshRequest request) {
        RBucket<Long> bucket = redissonClient.getBucket("refresh:" + request.getRefreshToken());
        Long patientId = bucket.get();
        if (patientId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh token expired or already used");
        }
        bucket.delete();

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Patient not found"));
        PatientAuth auth = patientAuthRepository.findByPatientId(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Patient account not found"));
        if (auth.getStatus() != null && auth.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("PATIENT"));
        claims.put("patientId", patient.getId());

        String token = jwtUtils.generateToken(patient.getId(), auth.getUsername(), claims);
        String newRefreshToken = generateRefreshToken(patient.getId());

        return Result.ok(new PatientLoginResponse(token, newRefreshToken,
                patient.getId(), patient.getName(), auth.getUsername()));
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

    private String generateRefreshToken(Long patientId) {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RBucket<Long> bucket = redissonClient.getBucket("refresh:" + token);
        bucket.set(patientId, Duration.ofMillis(refreshExpiration));
        return token;
    }

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
