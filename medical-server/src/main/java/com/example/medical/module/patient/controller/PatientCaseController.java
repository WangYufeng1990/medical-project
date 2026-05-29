package com.example.medical.module.patient.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.fhir.FhirBundle;
import com.example.medical.module.patient.service.PatientCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientCaseController {

    private final PatientCaseService patientCaseService;

    @GetMapping("/{id}/case")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<FhirBundle> getCase(@PathVariable Long id) {
        return Result.ok(patientCaseService.getPatientCase(id));
    }
}
