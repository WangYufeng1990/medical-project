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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientAuthController {

    private static final int LOCK_DURATION_MINUTES = 15;
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRY_SECONDS = 2592000L; // 30 days

    private final PatientRepository patientRepository;
    private final PatientAuthRepository patientAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final com.example.medical.common.audit.AuditLogWriter auditLogWriter;
    private final jakarta.servlet.http.HttpServletRequest httpRequest;

    @Value("${app.security.patient-token-expiry-seconds:86400}")
    private long patientTokenExpirySeconds;

    @PostMapping("/login")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "auth", action = "PATIENT_LOGIN_SUCCESS")
    public Result<PatientLoginResponse> login(@Valid @RequestBody PatientLoginRequest request) {
        PatientAuth auth = patientAuthRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    auditLoginFailure(null, request.getUsername(), "USER_NOT_FOUND");
                    return new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
                });
        if (auth.getStatus() != null && auth.getStatus() == 0) {
            auditLoginFailure(auth.getPatientId(), request.getUsername(), "ACCOUNT_DISABLED");
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }
        if (isLocked(auth)) {
            auditLoginFailure(auth.getPatientId(), request.getUsername(), "ACCOUNT_LOCKED");
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Account is temporarily locked. Try again later.");
        }

        Patient patient = patientRepository.findById(auth.getPatientId())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Patient record not found"));

        if (!passwordEncoder.matches(request.getPassword(), auth.getPassword())) {
            recordFailedAttempt(auth);
            auditLoginFailure(auth.getPatientId(), request.getUsername(), "BAD_CREDENTIALS");
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }
        resetFailedAttempts(auth);

        String token = generateToken(patient.getId(), auth.getUsername());
        String refreshToken = generateRefreshToken(patient.getId(), auth.getUsername());

        return Result.ok(new PatientLoginResponse(token, refreshToken,
                patient.getId(), patient.getName(), auth.getUsername()));
    }

    @PostMapping("/refresh")
    @com.example.medical.common.audit.Auditable(module = "auth", action = "PATIENT_TOKEN_REFRESH")
    public Result<PatientLoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Refresh token is required");
        }
        // Parse and validate the refresh token directly via Nimbus JOSE
        try {
            com.nimbusds.jwt.SignedJWT signedJwt = com.nimbusds.jwt.SignedJWT.parse(request.getRefreshToken());
            var claims = signedJwt.getJWTClaimsSet();

            // Verify scope contains "refresh" and roles contains "PATIENT"
            Object scpRaw = claims.getClaim("scp");
            Object rolesRaw = claims.getClaim("roles");
            boolean hasRefreshScope = false;
            boolean hasPatientRole = false;

            if (scpRaw instanceof java.util.List<?> scpList) {
                hasRefreshScope = scpList.contains("refresh");
            }
            if (rolesRaw instanceof java.util.List<?> rolesList) {
                hasPatientRole = rolesList.contains("PATIENT");
            }
            if (!hasRefreshScope || !hasPatientRole) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid refresh token");
            }

            // Check expiration
            var exp = claims.getExpirationTime();
            if (exp != null && exp.before(new java.util.Date())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh token expired");
            }

            Long patientId;
            try {
                patientId = Long.valueOf(claims.getSubject());
            } catch (NumberFormatException e) {
                // fallback: try uid claim or jti
                Object uid = claims.getClaim("uid");
                if (uid instanceof String s) {
                    patientId = Long.valueOf(s);
                } else {
                    throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid refresh token");
                }
            }

            String username = (String) claims.getClaim("username");
            if (username == null) username = "patient";

            String newToken = generateToken(patientId, username);
            String newRefreshToken = generateRefreshToken(patientId, username);

            return Result.ok(new PatientLoginResponse(newToken, newRefreshToken,
                    null, null, null));
        } catch (java.text.ParseException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid refresh token");
        }
    }

    private String generateToken(Long patientId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .id(patientId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(patientTokenExpirySeconds))
                .claim("uid", patientId.toString())
                .claim("roles", List.of("PATIENT"))
                .claim("scp", List.of("openid", "profile", "patient/Patient.read", "patient/Observation.read"))
                .claim("perm", List.of("patient:read", "patient:profile", "patient:appointments", "patient:prescriptions", "patient:bills", "patient:chat"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateRefreshToken(Long patientId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(patientId.toString())
                .id(patientId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(DEFAULT_REFRESH_TOKEN_EXPIRY_SECONDS))
                .claim("uid", patientId.toString())
                .claim("username", username)
                .claim("roles", List.of("PATIENT"))
                .claim("scp", List.of("refresh"))
                .issuer("https://medical-server/patient/refresh")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void recordFailedAttempt(PatientAuth auth) {
        patientAuthRepository.incrementFailedAttempts(auth.getId(),
                LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
    }

    private void resetFailedAttempts(PatientAuth auth) {
        patientAuthRepository.resetFailedAttempts(auth.getId());
        auth.setLastLoginTime(LocalDateTime.now());
        patientAuthRepository.save(auth);
    }

    private void auditLoginFailure(Long patientId, String username, String reason) {
        try {
            auditLogWriter.writeAsync(null, username, patientId,
                    "auth", "PATIENT_LOGIN_FAILED", username,
                    "reason=" + reason,
                    httpRequest != null ? httpRequest.getRemoteAddr() : "unknown",
                    java.time.Instant.now());
        } catch (Exception ignored) {
        }
    }

    private boolean isLocked(PatientAuth auth) {
        return auth.getLockedUntil() != null && auth.getLockedUntil().isAfter(LocalDateTime.now());
    }

    @Data
    static class PatientLoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    static class RefreshRequest {
        @NotBlank private String refreshToken;
    }

    @Data
    static class PatientLoginResponse {
        private final String token;
        private final String refreshToken;
        private final Long patientId;
        private final String name;
        private final String username;
    }
}
