package com.example.medical.module.quality.controller;

import com.example.medical.module.quality.dto.QualityReportVO;
import com.example.medical.common.result.Result;
import com.example.medical.module.quality.service.QualityMeasureService;
import com.example.medical.module.quality.dto.QualityMeasureVO;
import com.example.medical.module.quality.dto.QualityResultVO;
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
    public Result<List<QualityMeasureVO>> listMeasures() {
        return Result.ok(qualityMeasureService.listMeasures()
                .stream().map(QualityMeasureVO::fromEntity).toList());
    }

    @GetMapping("/measures/{cmsId}/report")
    public Result<QualityReportVO> getReport(@PathVariable String cmsId) {
        return Result.ok(qualityMeasureService.getReport(cmsId));
    }

    @PostMapping("/measures/{cmsId}/calculate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<QualityReportVO> calculateReport(@PathVariable String cmsId) {
        return Result.ok(qualityMeasureService.calculateReport(cmsId));
    }

    @GetMapping("/measures/{cmsId}/history")
    public Result<List<QualityResultVO>> getHistory(@PathVariable String cmsId) {
        return Result.ok(qualityMeasureService.getHistory(cmsId)
                .stream().map(QualityResultVO::fromEntity).toList());
    }
}
