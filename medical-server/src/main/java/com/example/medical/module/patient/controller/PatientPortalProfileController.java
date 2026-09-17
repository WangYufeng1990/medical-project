package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.AuditLogVO;
import com.example.medical.common.audit.repository.AuditLogRepository;
import com.example.medical.common.base.Pages;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.PatientPasswordChangeFormDTO;
import com.example.medical.module.patient.dto.PatientSelfUpdateFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.service.PatientAccountService;
import com.example.medical.module.patient.service.PatientService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The patient's own account: demographics, credential and access history. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalProfileController {

    private final PatientService patientService;
    private final PatientAccountService patientAccountService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public Result<PatientVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(patientService.profile(loginUser.getUserId()));
    }

    @PutMapping
    public Result<Void> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody PatientSelfUpdateFormDTO form) {
        patientService.updateOwnProfile(loginUser.getUserId(), form);
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @Valid @RequestBody PatientPasswordChangeFormDTO form) {
        patientAccountService.changePassword(loginUser.getUserId(), form.getOldPassword(), form.getNewPassword());
        return Result.ok();
    }

    @GetMapping("/disclosures")
    public Result<PageResult<AuditLogVO>> myDisclosures(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        var result = auditLogRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), loginUser.getUserId()),
                pageable);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent().stream().map(AuditLogVO::fromEntity).toList()));
    }
}
