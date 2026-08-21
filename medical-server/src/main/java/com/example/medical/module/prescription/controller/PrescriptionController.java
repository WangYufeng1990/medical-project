package com.example.medical.module.prescription.controller;

import org.springframework.data.domain.Page;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.prescription.dto.PrescriptionFormDTO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.prescription.service.EpcsService;
import com.example.medical.module.prescription.service.NcpdpScriptService;
import com.example.medical.module.prescription.service.PrescriptionService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final NcpdpScriptService ncpdpScriptService;
    private final EpcsService epcsService;
    private final com.example.medical.common.security.DoctorPatientScope doctorPatientScope;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<PrescriptionVO>> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        Page<PrescriptionVO> result = prescriptionService.page(page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PrescriptionVO> getById(@PathVariable Long id) {
        return Result.ok(prescriptionService.getById(id));
    }

    @GetMapping("/by-patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<PrescriptionVO>> getByPatientId(@PathVariable Long patientId) {
        return Result.ok(prescriptionService.getByPatientId(patientId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> create(@Valid @RequestBody PrescriptionFormDTO dto,
                               @AuthenticationPrincipal LoginUser loginUser) {
        prescriptionService.create(dto, loginUser);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        prescriptionService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/transmit")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "prescription", action = "TRANSMIT", phiAccess = true)
    public Result<Map<String, Object>> transmit(@PathVariable Long id,
                                                @RequestParam Long pharmacyId) {
        Prescription p = prescriptionRepository.findById(id)
                .orElseThrow(() -> new com.example.medical.common.exception.BusinessException(
                        com.example.medical.common.enums.ResultCode.NOT_FOUND, "Prescription not found"));
        doctorPatientScope.requireAccess(p.getPatientId());
        if (!"active".equals(p.getRxStatus())) {
            throw new com.example.medical.common.exception.BusinessException(
                    com.example.medical.common.enums.ResultCode.CONFLICT,
                    "Only active prescriptions can be transmitted");
        }
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(id);

        // Fail-closed EPCS gate (Review III C4): controlled substances are
        // rejected until a real EPCS channel exists.
        epcsService.assertTransmissionSupported(p, pharmacyId);

        String xml = ncpdpScriptService.generateNewRxXml(p, items, pharmacyId);

        // Do NOT claim "transmitted" — there is no transmission channel yet.
        // "generated" keeps rx_status honest (draft XML ready for review).
        p.setRxStatus("generated");
        prescriptionRepository.save(p);

        return Result.ok(Map.of("status", "generated", "format", "NCPDP SCRIPT (draft)",
                "messageId", "RX-" + id, "xml", xml));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> cancel(@PathVariable Long id) {
        prescriptionService.cancel(id);
        return Result.ok();
    }
}
