package com.example.medical.module.patient.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.PatientFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.AllergyEntry;
import com.example.medical.module.patient.entity.MedicalHistoryEntry;
import com.example.medical.module.patient.repository.AllergyEntryRepository;
import com.example.medical.module.patient.repository.MedicalHistoryEntryRepository;
import com.example.medical.module.patient.service.PatientService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final MedicalHistoryEntryRepository historyRepo;
    private final AllergyEntryRepository allergyRepo;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<PatientVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        Page<PatientVO> result = patientService.page(page, size, keyword);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PatientVO> getById(@PathVariable Long id) {
        enforceEmergencyScope(id);
        return Result.ok(patientService.getById(id));
    }

    private void enforceEmergencyScope(Long requestedPatientId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            if ("EMERGENCY".equals(user.getScope())) {
                if (user.getEmergencyPatientId() == null
                        || !user.getEmergencyPatientId().equals(requestedPatientId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN,
                            "Emergency access restricted to patient " + user.getEmergencyPatientId());
                }
            }
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> create(@Valid @RequestBody PatientFormDTO dto) {
        patientService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PatientFormDTO dto) {
        patientService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{patientId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<MedicalHistoryEntry>> getHistory(@PathVariable Long patientId) {
        return Result.ok(historyRepo.findByPatientIdOrderByCreateTimeDesc(patientId));
    }

    @PostMapping("/{patientId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "patient", action = "ADD_HISTORY")
    public Result<Void> addHistory(@PathVariable Long patientId,
                                    @Valid @RequestBody AddEntryRequest request) {
        MedicalHistoryEntry e = new MedicalHistoryEntry();
        e.setPatientId(patientId);
        e.setDescription(request.getDescription());
        e.setRecordedBy(resolveUserId());
        historyRepo.save(e);
        return Result.ok();
    }

    @GetMapping("/{patientId}/allergies")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<AllergyEntry>> getAllergies(@PathVariable Long patientId) {
        return Result.ok(allergyRepo.findByPatientIdOrderByCreateTimeDesc(patientId));
    }

    @PostMapping("/{patientId}/allergies")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "patient", action = "ADD_ALLERGY")
    public Result<Void> addAllergy(@PathVariable Long patientId,
                                    @Valid @RequestBody AddAllergyRequest request) {
        AllergyEntry e = new AllergyEntry();
        e.setPatientId(patientId);
        e.setAllergen(request.getAllergen());
        e.setReaction(request.getReaction());
        e.setSeverity(request.getSeverity());
        e.setRecordedBy(resolveUserId());
        allergyRepo.save(e);
        return Result.ok();
    }

    @DeleteMapping("/{patientId}/allergies/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @com.example.medical.common.audit.Auditable(module = "patient", action = "REMOVE_ALLERGY")
    public Result<Void> removeAllergy(@PathVariable Long patientId, @PathVariable Long id) {
        AllergyEntry e = allergyRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Allergy entry not found"));
        if (!e.getPatientId().equals(patientId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Access denied");
        }
        allergyRepo.delete(e);
        return Result.ok();
    }

    private Long resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu.getUserId();
        }
        return null;
    }

    @Data
    static class AddEntryRequest {
        @NotBlank private String description;
    }

    @Data
    static class AddAllergyRequest {
        @NotBlank private String allergen;
        private String reaction;
        private String severity;
    }
}
