package com.example.medical.module.patient.controller;

import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.dto.ObservationVO;
import com.example.medical.module.patient.entity.LoincCatalog;
import com.example.medical.module.patient.repository.LoincCatalogRepository;
import com.example.medical.module.patient.service.LabAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LabResultController {

    private final LabAnalysisService labAnalysisService;
    private final LoincCatalogRepository loincCatalogRepository;
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping("/patients/{patientId}/observations")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<ObservationVO>> getObservations(
            @PathVariable Long patientId,
            @RequestParam(required = false) String loinc,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        doctorPatientScope.requireAccess(patientId);
        return Result.ok(labAnalysisService.pageObservations(patientId, loinc, page, size));
    }

    @GetMapping("/patients/{patientId}/observations/trend")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<ObservationVO>> getTrend(
            @PathVariable Long patientId,
            @RequestParam String loinc) {
        doctorPatientScope.requireAccess(patientId);
        return Result.ok(labAnalysisService.getTrend(patientId, loinc));
    }

    @GetMapping("/loinc/catalog")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
    public Result<List<LoincCatalog>> catalog() {
        return Result.ok(loincCatalogRepository.findAll());
    }

    @GetMapping("/loinc/panel/{parentCode}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<LoincCatalog>> panel(@PathVariable String parentCode) {
        return Result.ok(loincCatalogRepository.findByPanelParentCode(parentCode));
    }
}
