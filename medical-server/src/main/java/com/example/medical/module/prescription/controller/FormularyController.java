package com.example.medical.module.prescription.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.FormularyEntryVO;
import com.example.medical.module.prescription.repository.FormularyEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FormularyController {

    private final FormularyEntryRepository formularyEntryRepository;

    @GetMapping("/formulary/{rxnormCode}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<FormularyEntryVO>> listByDrug(@PathVariable String rxnormCode) {
        return Result.ok(formularyEntryRepository.findByRxnormCode(rxnormCode)
                .stream().map(FormularyEntryVO::fromEntity).toList());
    }
}
