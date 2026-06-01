package com.example.medical.module.patient.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Consent;
import com.example.medical.module.patient.repository.ConsentRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentRepository consentRepository;

    @GetMapping("/consent")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Consent>> listByPatient(@RequestParam Long patientId) {
        return Result.ok(consentRepository.findByPatientIdOrderByCreateTimeDesc(patientId));
    }

    @PostMapping("/consent")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody ConsentRequest request) {
        Consent c = new Consent();
        c.setPatientId(request.getPatientId());
        c.setConsentType(request.getConsentType());
        c.setScope(request.getScope());
        c.setStatus("active");
        c.setConsentDate(LocalDate.now());
        consentRepository.save(c);
        return Result.ok();
    }

    @PutMapping("/consent/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> revoke(@PathVariable Long id) {
        Consent c = consentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Consent not found"));
        c.setStatus("revoked");
        consentRepository.save(c);
        return Result.ok();
    }

    @GetMapping("/patient/me/consent")
    @PreAuthorize("hasRole('PATIENT')")
    public Result<List<Consent>> myConsent(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(consentRepository.findByPatientIdOrderByCreateTimeDesc(loginUser.getUserId()));
    }

    @Data
    static class ConsentRequest {
        @NotBlank private String consentType;
        private String scope;
        private Long patientId;
    }
}
