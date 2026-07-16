package com.example.medical.module.quality.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.quality.entity.QualityMeasure;
import com.example.medical.module.quality.entity.QualityResult;
import com.example.medical.module.quality.service.QualityMeasureService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/quality")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class QualityController {

    private final QualityMeasureService qualityMeasureService;

    @GetMapping("/measures")
    public Result<List<QualityMeasure>> listMeasures() {
        return Result.ok(qualityMeasureService.listMeasures());
    }

    @GetMapping("/measures/{cmsId}/report")
    public Result<Map<String, Object>> getReport(@PathVariable String cmsId) {
        return Result.ok(qualityMeasureService.calculateReport(cmsId));
    }

    @GetMapping("/measures/{cmsId}/history")
    public Result<List<QualityResult>> getHistory(@PathVariable String cmsId) {
        return Result.ok(qualityMeasureService.getHistory(cmsId));
    }
}
