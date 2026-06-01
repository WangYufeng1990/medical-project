package com.example.medical.module.prescription.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.entity.PharmacyDirectory;
import com.example.medical.module.prescription.repository.PharmacyDirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class PharmacyController {

    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;

    @GetMapping
    public Result<List<PharmacyDirectory>> list(
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String state) {
        if (zip != null && !zip.isBlank()) {
            return Result.ok(pharmacyDirectoryRepository.findByZipCodeStartingWith(zip.substring(0, Math.min(3, zip.length()))));
        }
        if (state != null && !state.isBlank()) {
            return Result.ok(pharmacyDirectoryRepository.findByStateOrderByNameAsc(state));
        }
        return Result.ok(pharmacyDirectoryRepository.findAll());
    }
}
