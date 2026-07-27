package com.example.medical.module.prescription.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.entity.FormularyEntry;
import com.example.medical.module.prescription.repository.FormularyEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FormularyController {

    private final FormularyEntryRepository formularyEntryRepository;

    @GetMapping("/formulary/check")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Map<String, Object>> check(@RequestParam String rxnormCode, @RequestParam String insurancePayer) {
        var entry = formularyEntryRepository.findByRxnormCodeAndInsurancePayer(rxnormCode, insurancePayer);
        if (entry.isEmpty()) {
            return Result.ok(Map.of("found", false, "message", "Not in formulary or unknown insurance"));
        }
        FormularyEntry e = entry.get();
        return Result.ok(Map.of(
                "found", true,
                "drugName", e.getDrugName(),
                "tier", e.getTier(),
                "priorAuthRequired", e.getPriorAuthRequired() != null && e.getPriorAuthRequired(),
                "stepTherapyRequired", e.getStepTherapyRequired() != null && e.getStepTherapyRequired(),
                "alternatives", e.getAlternatives() != null ? e.getAlternatives() : ""
        ));
    }

    @GetMapping("/formulary/{rxnormCode}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<FormularyEntry>> listByDrug(@PathVariable String rxnormCode) {
        return Result.ok(formularyEntryRepository.findByRxnormCode(rxnormCode));
    }
}
