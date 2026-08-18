package com.example.medical.module.system.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.system.entity.EmergencyAccess;
import com.example.medical.module.system.repository.EmergencyAccessRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/emergency")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class EmergencyAccessController {

    static final int EMERGENCY_ACCESS_MINUTES = 30;

    private final PatientRepository patientRepository;
    private final EmergencyAccessRepository emergencyAccessRepository;
    private final JwtEncoder jwtEncoder;

    @PostMapping("/access/{patientId}")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "emergency", action = "ACCESS")
    public Result<Map<String, Object>> emergencyAccess(@AuthenticationPrincipal LoginUser loginUser,
                                                        @PathVariable Long patientId,
                                                        @Valid @RequestBody EmergencyAccessRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        Instant now = Instant.now();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwtClaimsSet.builder()
                        .subject(loginUser.getUsername())
                        .id(loginUser.getUserId().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(EMERGENCY_ACCESS_MINUTES * 60L))
                        .claim("uid", loginUser.getUserId().toString())
                        .claim("roles", List.of("DOCTOR"))
                        .claim("scope", "EMERGENCY")
                        .claim("scp", List.of("EMERGENCY"))
                        .claim("patientId", patientId)
                        .build())).getTokenValue();

        EmergencyAccess ea = new EmergencyAccess();
        ea.setUserId(loginUser.getUserId());
        ea.setPatientId(patientId);
        ea.setReason(request.getReason());
        ea.setAccessedAt(LocalDateTime.now());
        ea.setExpiresAt(LocalDateTime.now().plusMinutes(EMERGENCY_ACCESS_MINUTES));
        emergencyAccessRepository.save(ea);

        log.warn("EMERGENCY ACCESS: user={} patient={} expiresIn={}min",
                loginUser.getUsername(), patientId, EMERGENCY_ACCESS_MINUTES);

        return Result.ok(Map.of("token", token, "expiresInMinutes", EMERGENCY_ACCESS_MINUTES,
                "patientId", patientId));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<java.util.List<EmergencyAccess>> history(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Integer audited) {
        if (audited != null) {
            return Result.ok(emergencyAccessRepository.findByAuditedOrderByAccessedAtDesc(audited));
        }
        if (patientId != null) {
            return Result.ok(emergencyAccessRepository.findByPatientIdOrderByAccessedAtDesc(patientId));
        }
        var pageable = org.springframework.data.domain.PageRequest.of(0, 500,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "accessedAt"));
        return Result.ok(emergencyAccessRepository.findAll(pageable).getContent());
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "emergency", action = "REVIEW")
    public Result<Void> review(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
        EmergencyAccess ea = emergencyAccessRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Emergency access record not found"));
        if (ea.getAudited() != null && ea.getAudited() == 1) {
            throw new BusinessException(ResultCode.CONFLICT, "Already reviewed");
        }
        ea.setAudited(1);
        ea.setReviewedBy(loginUser.getUserId());
        ea.setReviewedAt(LocalDateTime.now());
        emergencyAccessRepository.save(ea);
        log.info("Emergency access {} reviewed by {}", id, loginUser.getUsername());
        return Result.ok();
    }

    @Data
    static class EmergencyAccessRequest {
        @NotBlank(message = "Reason is required for emergency access")
        private String reason;
    }
}
