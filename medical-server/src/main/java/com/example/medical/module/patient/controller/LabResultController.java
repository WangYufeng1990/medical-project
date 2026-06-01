package com.example.medical.module.patient.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.LoincCatalog;
import com.example.medical.module.patient.entity.Observation;
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

    @GetMapping("/patients/{patientId}/observations")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<Observation>> getTrend(
            @PathVariable Long patientId,
            @RequestParam String loinc) {
        return Result.ok(labAnalysisService.getTrend(patientId, loinc));
    }

    @GetMapping("/loinc/catalog")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<LoincCatalog>> catalog() {
        return Result.ok(loincCatalogRepository.findAll());
    }

    @GetMapping("/loinc/panel/{parentCode}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<LoincCatalog>> panel(@PathVariable String parentCode) {
        return Result.ok(loincCatalogRepository.findByPanelParentCode(parentCode));
    }
}
