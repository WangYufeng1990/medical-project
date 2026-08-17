package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.dto.VitalSignVO;
import com.example.medical.module.patient.entity.VitalSign;
import com.example.medical.module.patient.repository.VitalSignRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VitalSignController {

    private final VitalSignRepository vitalSignRepository;
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping("/patients/{patientId}/vitals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<VitalSignVO>> list(@PathVariable Long patientId, PageQuery pageQuery) {
        doctorPatientScope.requireAccess(patientId);
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "recordedAt"));
        var page = vitalSignRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId), pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent().stream().map(VitalSignVO::fromEntity).toList()));
    }

    @PostMapping("/patients/{patientId}/vitals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "vital_sign", action = "CREATE")
    public Result<VitalSignVO> create(@PathVariable Long patientId, @Valid @RequestBody VitalSignForm form) {
        doctorPatientScope.requireAccess(patientId);
        VitalSign v = new VitalSign();
        v.setPatientId(patientId);
        v.setRecordedBy(form.getRecordedBy());
        v.setRecordedAt(form.getRecordedAt() != null ? form.getRecordedAt() : LocalDateTime.now());
        v.setSystolicBp(form.getSystolicBp());
        v.setDiastolicBp(form.getDiastolicBp());
        v.setHeartRate(form.getHeartRate());
        v.setTemperature(form.getTemperature());
        v.setRespiratoryRate(form.getRespiratoryRate());
        v.setOxygenSaturation(form.getOxygenSaturation());
        v.setHeightCm(form.getHeightCm());
        v.setWeightKg(form.getWeightKg());
        v.setBmi(form.getBmi());
        v.setNotes(form.getNotes());
        return Result.ok(VitalSignVO.fromEntity(vitalSignRepository.save(v)));
    }

    @Data
    static class VitalSignForm {
        private Long recordedBy;
        private LocalDateTime recordedAt;
        private Integer systolicBp;
        private Integer diastolicBp;
        private Integer heartRate;
        private BigDecimal temperature;
        private Integer respiratoryRate;
        private Integer oxygenSaturation;
        private BigDecimal heightCm;
        private BigDecimal weightKg;
        private BigDecimal bmi;
        private String notes;
    }
}
