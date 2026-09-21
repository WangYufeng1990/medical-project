package com.example.medical.module.integration.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.integration.dto.IntegrationAckVO;
import com.example.medical.common.result.Result;
import com.example.medical.module.integration.dto.AdtEventPayload;
import com.example.medical.module.integration.dto.LabResultPayload;
import com.example.medical.module.integration.service.AdtService;
import com.example.medical.module.integration.service.LabResultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.integration.api-key:}")
    private String integrationApiKey;

    private void checkApiKey(HttpServletRequest request) {
        if (integrationApiKey == null || integrationApiKey.isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Integration API key not configured on server");
        }
        String header = request.getHeader("X-Integration-Key");
        if (header == null || !integrationApiKey.equals(header)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Invalid or missing integration API key");
        }
    }

    @PostMapping("/adt")
    public Result<IntegrationAckVO> receiveAdt(@Valid @RequestBody AdtEventPayload event,
                                                   HttpServletRequest request) {
        checkApiKey(request);
        adtService.processAdt(event);
        return Result.ok(IntegrationAckVO.accepted(event.getSourceMessageId()));
    }

    @PostMapping("/lab-results")
    public Result<IntegrationAckVO> receiveLabResults(@Valid @RequestBody LabResultPayload dto,
                                                          HttpServletRequest request) {
        checkApiKey(request);
        int count = labResultService.processLabResults(dto);
        return Result.ok(IntegrationAckVO.accepted(dto.getSourceMessageId(), count));
    }
}
