package com.example.medical.module.billing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.dto.BillFormDTO;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<BillVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) Integer status) {
        IPage<BillVO> result = billService.page(page, size, status);
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<BillVO> getById(@PathVariable Long id) {
        return Result.ok(billService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> create(@Valid @RequestBody BillFormDTO dto) {
        billService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> pay(@PathVariable Long id) {
        billService.pay(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        billService.delete(id);
        return Result.ok();
    }
}
