package com.example.medical.module.prescription.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.CdsWarning;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.service.CdsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cds")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class CdsController {

    private final CdsService cdsService;

    @PostMapping("/check")
    public Result<CdsCheckResponse> check(@Valid @RequestBody CdsCheckRequest request) {
        List<PrescriptionItem> items = request.getItems().stream().map(i -> {
            PrescriptionItem item = new PrescriptionItem();
            item.setRxnormCode(i.getRxnormCode());
            item.setDrugName(i.getDrugName());
            return item;
        }).toList();

        List<CdsWarning> warnings = new java.util.ArrayList<>();
        warnings.addAll(cdsService.checkDrugInteractions(items));
        if (request.getPatientId() != null) {
            warnings.addAll(cdsService.checkAllergyContraindications(
                    request.getPatientId(), items));
        }

        CdsCheckResponse response = new CdsCheckResponse();
        response.setPassed(warnings.isEmpty());
        response.setWarnings(warnings);
        return Result.ok(response);
    }

    @Data
    static class CdsCheckRequest {
        private Long patientId;
        @NotEmpty private List<ItemRef> items;
    }

    @Data
    static class ItemRef {
        @NotNull private String rxnormCode;
        @NotNull private String drugName;
    }

    @Data
    static class CdsCheckResponse {
        private boolean passed;
        private List<CdsWarning> warnings;
    }
}
