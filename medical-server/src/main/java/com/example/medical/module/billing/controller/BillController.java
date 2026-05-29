package com.example.medical.module.billing.controller;

import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.dto.BillFormDTO;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.service.BillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/bills")
@RequiredArgsConstructor
public class BillController {

    private final BillService billService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<BillVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String claimStatus) {
        Page<BillVO> result = billService.page(page, size, claimStatus);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
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

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> submitClaim(@PathVariable Long id) {
        billService.submitClaim(id);
        return Result.ok();
    }

    @PutMapping("/{id}/adjudicate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> adjudicate(@PathVariable Long id,
                                   @Valid @RequestBody AdjudicateRequest request) {
        billService.adjudicate(id, request.getAdjustment(),
                request.getInsurancePayment(), request.getClaimNumber(),
                request.getAdjudicationDate());
        return Result.ok();
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> pay(@PathVariable Long id,
                            @Valid @RequestBody PayRequest request) {
        billService.pay(id, request.getPaymentAmount(), request.getPaymentMethod());
        return Result.ok();
    }

    @PutMapping("/{id}/deny")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> denyClaim(@PathVariable Long id,
                                  @RequestBody DenyRequest request) {
        billService.denyClaim(id, request.getReason());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        billService.delete(id);
        return Result.ok();
    }

    @Data
    static class AdjudicateRequest {
        private BigDecimal adjustment;
        @Positive private BigDecimal insurancePayment;
        private String claimNumber;
        private LocalDate adjudicationDate;
    }

    @Data
    static class PayRequest {
        @Positive private BigDecimal paymentAmount;
        @NotBlank private String paymentMethod;
    }

    @Data
    static class DenyRequest {
        private String reason;
    }
}
