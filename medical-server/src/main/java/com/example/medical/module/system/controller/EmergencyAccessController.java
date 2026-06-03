package com.example.medical.module.system.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.system.entity.EmergencyAccess;
import com.example.medical.module.system.repository.EmergencyAccessRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/emergency")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class EmergencyAccessController {

    private static final int EMERGENCY_ACCESS_MINUTES = 30;

    private final PatientRepository patientRepository;
    private final EmergencyAccessRepository emergencyAccessRepository;

    @PostMapping("/access/{patientId}")
    public Result<PatientVO> emergencyAccess(@AuthenticationPrincipal LoginUser loginUser,
                                              @PathVariable Long patientId,
                                              @Valid @RequestBody EmergencyAccessRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        EmergencyAccess ea = new EmergencyAccess();
        ea.setUserId(loginUser.getUserId());
        ea.setPatientId(patientId);
        ea.setReason(request.getReason());
        ea.setAccessedAt(LocalDateTime.now());
        ea.setExpiresAt(LocalDateTime.now().plusMinutes(EMERGENCY_ACCESS_MINUTES));
        ea.setAudited(1);
        emergencyAccessRepository.save(ea);

        log.warn("EMERGENCY ACCESS: user={} accessed patient={} reason={}",
                loginUser.getUsername(), patientId, request.getReason());

        return Result.ok(PatientVO.fromEntity(patient));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<java.util.List<EmergencyAccess>> history(
            @RequestParam(required = false) Long patientId) {
        if (patientId != null) {
            return Result.ok(emergencyAccessRepository.findByPatientIdOrderByAccessedAtDesc(patientId));
        }
        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(0, 500,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "accessedAt"));
        return Result.ok(emergencyAccessRepository.findAll(pageable).getContent());
    }

    @Data
    static class EmergencyAccessRequest {
        @NotBlank(message = "Reason is required for emergency access")
        private String reason;
    }
}
