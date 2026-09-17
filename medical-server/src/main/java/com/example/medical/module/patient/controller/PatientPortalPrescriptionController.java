package com.example.medical.module.patient.controller;

import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.service.PrescriptionService;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The patient's own prescriptions. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalPrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping("/prescriptions")
    public Result<PageResult<PrescriptionVO>> myPrescriptions(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var result = prescriptionService.pageForPatient(loginUser.getUserId(), page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }
}
