package com.example.medical.module.prescription.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.entity.RefillRequest;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.prescription.repository.RefillRequestRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RefillController {

    private final RefillRequestRepository refillRequestRepository;
    private final PrescriptionRepository prescriptionRepository;

    @PostMapping("/patient/me/refill-requests")
    @PreAuthorize("hasRole('PATIENT')")
    @Transactional
    @Auditable(module = "refill_request", action = "CREATE")
    public Result<RefillRequest> create(@AuthenticationPrincipal LoginUser loginUser,
                                         @Valid @RequestBody RefillForm form) {
        if (!prescriptionRepository.existsById(form.getPrescriptionId())) {
            return Result.fail(404, "Prescription not found");
        }
        RefillRequest r = new RefillRequest();
        r.setPatientId(loginUser.getUserId());
        r.setPrescriptionId(form.getPrescriptionId());
        r.setStatus("PENDING");
        r.setRequestedAt(LocalDateTime.now());
        r.setReason(form.getReason());
        return Result.ok(refillRequestRepository.save(r));
    }

    @GetMapping("/patient/me/refill-requests")
    @PreAuthorize("hasRole('PATIENT')")
    public Result<List<RefillRequest>> listMine(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(refillRequestRepository.findByPatientIdOrderByRequestedAtDesc(loginUser.getUserId()));
    }

    @GetMapping("/prescriptions/refill-requests")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<RefillRequest>> listPending() {
        return Result.ok(refillRequestRepository.findByStatusOrderByRequestedAtDesc("PENDING"));
    }

    @PutMapping("/prescriptions/refill-requests/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "refill_request", action = "APPROVE")
    public Result<RefillRequest> approve(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
        RefillRequest r = refillRequestRepository.findById(id).orElseThrow();
        r.setStatus("APPROVED");
        r.setReviewedBy(loginUser.getUserId());
        r.setReviewedAt(LocalDateTime.now());
        return Result.ok(refillRequestRepository.save(r));
    }

    @PutMapping("/prescriptions/refill-requests/{id}/deny")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "refill_request", action = "DENY")
    public Result<RefillRequest> deny(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser,
                                       @RequestBody(required = false) DenyForm form) {
        RefillRequest r = refillRequestRepository.findById(id).orElseThrow();
        r.setStatus("DENIED");
        r.setReviewedBy(loginUser.getUserId());
        r.setReviewedAt(LocalDateTime.now());
        if (form != null && form.getNotes() != null) r.setReviewNotes(form.getNotes());
        return Result.ok(refillRequestRepository.save(r));
    }

    @Data
    static class RefillForm {
        @NotNull private Long prescriptionId;
        private String reason;
    }

    @Data
    static class DenyForm {
        private String notes;
    }
}
