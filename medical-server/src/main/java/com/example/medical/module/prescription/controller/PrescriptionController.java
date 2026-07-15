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
    public Result<Void> create(@Valid @RequestBody PrescriptionFormDTO dto) {
        prescriptionService.create(dto);
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
    public Result<Map<String, Object>> transmit(@PathVariable Long id,
                                                @RequestParam Long pharmacyId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        Prescription p = prescriptionRepository.findById(id)
                .orElseThrow(() -> new com.example.medical.common.exception.BusinessException(
                        com.example.medical.common.enums.ResultCode.NOT_FOUND, "Prescription not found"));
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(id);

        epcsService.auditEpcsTransmission(p, loginUser.getUserId());

        String xml = ncpdpScriptService.generateNewRxXml(p, items, pharmacyId);

        p.setRxStatus("transmitted");
        prescriptionRepository.save(p);

        return Result.ok(Map.of("status", "transmitted", "format", "NCPDP SCRIPT 10.6",
                "messageId", "RX-" + id, "xml", xml));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> cancel(@PathVariable Long id) {
        prescriptionService.cancel(id);
        return Result.ok();
    }
}
