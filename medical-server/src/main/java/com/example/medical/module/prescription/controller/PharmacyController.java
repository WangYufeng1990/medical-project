package com.example.medical.module.prescription.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.PharmacyVO;
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
    public Result<List<PharmacyVO>> list(
            @RequestParam(required = false) String zip,
            @RequestParam(required = false) String state) {
        List<PharmacyDirectory> pharmacies;
        if (zip != null && !zip.isBlank()) {
            pharmacies = pharmacyDirectoryRepository.findByZipCodeStartingWith(zip.substring(0, Math.min(3, zip.length())));
        } else if (state != null && !state.isBlank()) {
            pharmacies = pharmacyDirectoryRepository.findByStateOrderByNameAsc(state);
        } else {
            pharmacies = pharmacyDirectoryRepository.findAll();
        }
        return Result.ok(pharmacies.stream().map(PharmacyVO::fromEntity).toList());
    }
}
