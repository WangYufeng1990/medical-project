package com.example.medical.module.patient.controller;

import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.service.BillService;
import com.example.medical.module.patient.dto.PatientPayBillFormDTO;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The patient's own bills and payments. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalBillingController {

    private final BillService billService;

    @GetMapping("/bills")
    public Result<PageResult<BillVO>> myBills(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var result = billService.pageForPatient(loginUser.getUserId(), page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @PutMapping("/bills/{id}/pay")
    public Result<Void> payMyBill(@AuthenticationPrincipal LoginUser loginUser,
                                  @PathVariable Long id,
                                  @Valid @RequestBody PatientPayBillFormDTO form) {
        billService.payByPatient(id, loginUser.getUserId(), form.getPaymentAmount(), form.getPaymentMethod());
        return Result.ok();
    }
}
