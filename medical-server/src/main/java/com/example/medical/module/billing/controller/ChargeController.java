package com.example.medical.module.billing.controller;

import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.dto.ChargeForm;
import com.example.medical.module.billing.dto.ChargeVO;
import com.example.medical.module.billing.service.ChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ChargeController {

    private final ChargeService chargeService;

    @GetMapping
    public Result<PageResult<ChargeVO>> list(@RequestParam(required = false) Long patientId, @Valid PageQuery pageQuery) {
        var page = chargeService.list(patientId, pageQuery);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent()));
    }

    @PostMapping
    public Result<ChargeVO> create(@Valid @RequestBody ChargeForm form) {
        return Result.ok(chargeService.create(form));
    }

    @PutMapping("/{id}/convert")
    public Result<BillVO> convert(@PathVariable Long id) {
        return Result.ok(chargeService.convert(id));
    }
}
