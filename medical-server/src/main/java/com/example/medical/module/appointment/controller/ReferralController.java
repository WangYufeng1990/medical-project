package com.example.medical.module.appointment.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.appointment.dto.ReferralVO;
import com.example.medical.module.appointment.entity.Referral;
import com.example.medical.module.appointment.repository.ReferralRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralRepository referralRepository;
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping("/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<ReferralVO>> list(@RequestParam(required = false) Long patientId, PageQuery pageQuery) {
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "referralDate"));
        org.springframework.data.jpa.domain.Specification<Referral> spec = null;
        if (patientId != null) {
            doctorPatientScope.requireAccess(patientId);
            spec = (root, query, cb) -> cb.equal(root.get("patientId"), patientId);
        } else {
            var scope = doctorPatientScope.resolve();
            if (scope != null) spec = (root, query, cb) -> root.get("patientId").in(scope);
        }
        var page = referralRepository.findAll(spec, pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent().stream().map(ReferralVO::fromEntity).toList()));
    }

    @GetMapping("/patients/{patientId}/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<List<ReferralVO>> listByPatient(@PathVariable Long patientId) {
        doctorPatientScope.requireAccess(patientId);
        return Result.ok(referralRepository.findByPatientIdOrderByReferralDateDesc(patientId)
                .stream().map(ReferralVO::fromEntity).toList());
    }

    @PostMapping("/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "referral", action = "CREATE")
    public Result<ReferralVO> create(@Valid @RequestBody ReferralForm form, @AuthenticationPrincipal LoginUser loginUser) {
        doctorPatientScope.requireAccess(form.getPatientId());
        Referral r = new Referral();
        r.setPatientId(form.getPatientId());
        // referring_doctor_id is NOT NULL; the frontend form never sends it, so
        // default to the authenticated doctor creating the referral.
        r.setReferringDoctorId(form.getReferringDoctorId() != null ? form.getReferringDoctorId() : loginUser.getUserId());
        r.setSpecialistName(form.getSpecialistName());
        r.setSpecialistNpi(form.getSpecialistNpi());
        r.setSpecialty(form.getSpecialty());
        r.setDiagnosis(form.getDiagnosis());
        r.setReason(form.getReason());
        r.setUrgency(form.getUrgency() != null ? form.getUrgency() : "ROUTINE");
        r.setStatus("PENDING");
        r.setReferralDate(form.getReferralDate() != null ? form.getReferralDate() : LocalDate.now());
        r.setAppointmentDate(form.getAppointmentDate());
        r.setNotes(form.getNotes());
        return Result.ok(ReferralVO.fromEntity(referralRepository.save(r)));
    }

    @PutMapping("/referrals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "referral", action = "UPDATE")
    public Result<ReferralVO> update(@PathVariable Long id, @Valid @RequestBody ReferralForm form) {
        Referral r = referralRepository.findById(id).orElseThrow();
        doctorPatientScope.requireAccess(r.getPatientId());
        if (form.getStatus() != null) r.setStatus(form.getStatus());
        if (form.getAppointmentDate() != null) r.setAppointmentDate(form.getAppointmentDate());
        if (form.getCompletionDate() != null) r.setCompletionDate(form.getCompletionDate());
        if (form.getNotes() != null) r.setNotes(form.getNotes());
        return Result.ok(ReferralVO.fromEntity(referralRepository.save(r)));
    }

    @Data
    static class ReferralForm {
        @jakarta.validation.constraints.NotNull
        private Long patientId;
        private Long referringDoctorId;
        private String specialistName;
        private String specialistNpi;
        private String specialty;
        private String diagnosis;
        private String reason;
        private String urgency;
        private String status;
        private LocalDate referralDate;
        private LocalDate appointmentDate;
        private LocalDate completionDate;
        private String notes;
    }
}
