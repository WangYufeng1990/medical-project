package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.dto.ProblemVO;
import com.example.medical.module.patient.entity.Problem;
import com.example.medical.module.patient.repository.ProblemRepository;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemRepository problemRepository;
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping("/patients/{patientId}/problems")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<ProblemVO>> list(@PathVariable Long patientId, @Valid PageQuery pageQuery) {
        doctorPatientScope.requireAccess(patientId);
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "onsetDate"));
        var page = problemRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId), pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent().stream().map(ProblemVO::fromEntity).toList()));
    }

    @PostMapping("/patients/{patientId}/problems")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "problem", action = "CREATE", phiAccess = true)
    public Result<ProblemVO> create(@PathVariable Long patientId, @Valid @RequestBody ProblemForm form) {
        doctorPatientScope.requireAccess(patientId);
        Problem p = new Problem();
        p.setPatientId(patientId);
        p.setSnomedCode(form.getSnomedCode());
        p.setSnomedDisplay(form.getSnomedDisplay());
        p.setIcd10Code(form.getIcd10Code());
        p.setOnsetDate(form.getOnsetDate() != null ? form.getOnsetDate() : LocalDate.now());
        p.setResolutionDate(form.getResolutionDate());
        p.setStatus(form.getStatus() != null ? form.getStatus() : "ACTIVE");
        p.setSeverity(form.getSeverity());
        p.setRecordedBy(form.getRecordedBy());
        p.setNotes(form.getNotes());
        return Result.ok(ProblemVO.fromEntity(problemRepository.save(p)));
    }

    @PutMapping("/patients/{patientId}/problems/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "problem", action = "UPDATE", phiAccess = true)
    public Result<ProblemVO> update(@PathVariable Long patientId, @PathVariable Long id, @Valid @RequestBody ProblemForm form) {
        doctorPatientScope.requireAccess(patientId);
        Problem p = problemRepository.findById(id)
                .orElseThrow(() -> new com.example.medical.common.exception.BusinessException(
                        com.example.medical.common.enums.ResultCode.NOT_FOUND, "Problem not found"));
        // Cross-patient mutation guard (Review III C3).
        if (!p.getPatientId().equals(patientId)) {
            throw new com.example.medical.common.exception.BusinessException(
                    com.example.medical.common.enums.ResultCode.FORBIDDEN, "Access denied");
        }
        if (form.getSnomedCode() != null) p.setSnomedCode(form.getSnomedCode());
        if (form.getSnomedDisplay() != null) p.setSnomedDisplay(form.getSnomedDisplay());
        if (form.getIcd10Code() != null) p.setIcd10Code(form.getIcd10Code());
        if (form.getOnsetDate() != null) p.setOnsetDate(form.getOnsetDate());
        if (form.getResolutionDate() != null) p.setResolutionDate(form.getResolutionDate());
        if (form.getStatus() != null) p.setStatus(form.getStatus());
        if (form.getSeverity() != null) p.setSeverity(form.getSeverity());
        if (form.getNotes() != null) p.setNotes(form.getNotes());
        return Result.ok(ProblemVO.fromEntity(problemRepository.save(p)));
    }

    @Data
    static class ProblemForm {
        private String snomedCode;
        private String snomedDisplay;
        private String icd10Code;
        private LocalDate onsetDate;
        private LocalDate resolutionDate;
        private String status;
        private String severity;
        private Long recordedBy;
        private String notes;
    }
}
