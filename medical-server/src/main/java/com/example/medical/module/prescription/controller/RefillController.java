package com.example.medical.module.prescription.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.prescription.dto.RefillRequestVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.entity.RefillRequest;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.prescription.repository.RefillRequestRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
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
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final DoctorPatientScope doctorPatientScope;

    @PostMapping("/patient/me/refill-requests")
    @PreAuthorize("hasRole('PATIENT')")
    @Transactional
    @Auditable(module = "refill_request", action = "CREATE", phiAccess = true)
    public Result<RefillRequestVO> create(@AuthenticationPrincipal LoginUser loginUser,
                                          @Valid @RequestBody RefillForm form) {
        // Ownership + state validation (Review III C8): the prescription must
        // belong to the requesting patient and be active; no duplicate PENDING.
        Prescription p = prescriptionRepository.findById(form.getPrescriptionId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Prescription not found"));
        if (!p.getPatientId().equals(loginUser.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Access denied");
        }
        if (!"active".equals(p.getRxStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Only active prescriptions can be refilled");
        }
        if (refillRequestRepository.existsByPrescriptionIdAndStatus(form.getPrescriptionId(), "PENDING")) {
            throw new BusinessException(ResultCode.CONFLICT, "A refill request is already pending for this prescription");
        }
        RefillRequest r = new RefillRequest();
        r.setPatientId(p.getPatientId());
        r.setPrescriptionId(form.getPrescriptionId());
        r.setStatus("PENDING");
        r.setRequestedAt(LocalDateTime.now());
        r.setReason(form.getReason());
        return Result.ok(RefillRequestVO.fromEntity(refillRequestRepository.save(r)));
    }

    @GetMapping("/patient/me/refill-requests")
    @PreAuthorize("hasRole('PATIENT')")
    public Result<List<RefillRequestVO>> listMine(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(refillRequestRepository.findByPatientIdOrderByRequestedAtDesc(loginUser.getUserId())
                .stream().map(RefillRequestVO::fromEntity).toList());
    }

    @GetMapping("/prescriptions/refill-requests")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<RefillRequestVO>> listPending() {
        var scope = doctorPatientScope.resolve();
        List<RefillRequest> pending = scope == null
                ? refillRequestRepository.findByStatusOrderByRequestedAtDesc("PENDING")
                : refillRequestRepository.findByStatusAndPatientIdInOrderByRequestedAtDesc("PENDING", scope);
        return Result.ok(pending.stream().map(RefillRequestVO::fromEntity).toList());
    }

    @PutMapping("/prescriptions/refill-requests/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "refill_request", action = "APPROVE")
    public Result<RefillRequestVO> approve(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser) {
        RefillRequest r = refillRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Refill request not found"));
        doctorPatientScope.requireAccess(r.getPatientId());
        // Consume one refill from the prescription's items (Review III H7) —
        // approve must be backed by an actual refill budget.
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(r.getPrescriptionId());
        PrescriptionItem withRefill = items.stream()
                .filter(it -> it.getRefills() != null && it.getRefills() > 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.CONFLICT,
                        "No refills remaining on this prescription"));
        withRefill.setRefills(withRefill.getRefills() - 1);
        prescriptionItemRepository.save(withRefill);

        r.setStatus("APPROVED");
        r.setReviewedBy(loginUser.getUserId());
        r.setReviewedAt(LocalDateTime.now());
        return Result.ok(RefillRequestVO.fromEntity(refillRequestRepository.save(r)));
    }

    @PutMapping("/prescriptions/refill-requests/{id}/deny")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "refill_request", action = "DENY", phiAccess = true)
    public Result<RefillRequestVO> deny(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser,
                                        @RequestBody(required = false) DenyForm form) {
        RefillRequest r = refillRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Refill request not found"));
        doctorPatientScope.requireAccess(r.getPatientId());
        r.setStatus("DENIED");
        r.setReviewedBy(loginUser.getUserId());
        r.setReviewedAt(LocalDateTime.now());
        if (form != null && form.getNotes() != null) r.setReviewNotes(form.getNotes());
        return Result.ok(RefillRequestVO.fromEntity(refillRequestRepository.save(r)));
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
