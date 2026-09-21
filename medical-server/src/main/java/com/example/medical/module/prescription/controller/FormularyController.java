package com.example.medical.module.prescription.controller;

import com.example.medical.module.prescription.dto.FormularyCheckVO;
import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.FormularyEntryVO;
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
    public Result<FormularyCheckVO> check(@RequestParam String rxnormCode, @RequestParam String insurancePayer) {
        var entry = formularyEntryRepository.findByRxnormCodeAndInsurancePayer(rxnormCode, insurancePayer);
        if (entry.isEmpty()) {
            return Result.ok(FormularyCheckVO.notFound());
        }
        return Result.ok(FormularyCheckVO.fromEntity(entry.get()));
    }

    @GetMapping("/formulary/{rxnormCode}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<FormularyEntryVO>> listByDrug(@PathVariable String rxnormCode) {
        return Result.ok(formularyEntryRepository.findByRxnormCode(rxnormCode)
                .stream().map(FormularyEntryVO::fromEntity).toList());
    }
}
