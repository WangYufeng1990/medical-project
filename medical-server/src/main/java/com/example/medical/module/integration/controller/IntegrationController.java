package com.example.medical.module.integration.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.integration.dto.AdtEventPayload;
import com.example.medical.module.integration.dto.LabResultPayload;
import com.example.medical.module.integration.service.AdtService;
import com.example.medical.module.integration.service.LabResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class IntegrationController {

    private final AdtService adtService;
    private final LabResultService labResultService;

    @PostMapping("/adt")
    public Result<Map<String, Object>> receiveAdt(@Valid @RequestBody AdtEventPayload event) {
        adtService.processAdt(event);
        return Result.ok(Map.of("status", "ACK", "sourceMessageId", event.getSourceMessageId()));
    }

    @PostMapping("/lab-results")
    public Result<Map<String, Object>> receiveLabResults(@Valid @RequestBody LabResultPayload dto) {
        int count = labResultService.processLabResults(dto);
        return Result.ok(Map.of("status", "ACK", "recordsCreated", count,
                "sourceMessageId", dto.getSourceMessageId()));
    }
}
