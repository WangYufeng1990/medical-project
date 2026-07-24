package com.example.medical.module.appointment.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.entity.Referral;
import com.example.medical.module.appointment.repository.ReferralRepository;
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
public class ReferralController {

    private final ReferralRepository referralRepository;

    @GetMapping("/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<Referral>> list(@RequestParam(required = false) Long patientId, PageQuery pageQuery) {
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "referralDate"));
        org.springframework.data.jpa.domain.Specification<Referral> spec = patientId != null
                ? (root, query, cb) -> cb.equal(root.get("patientId"), patientId)
                : null;
        var page = referralRepository.findAll(spec, pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent()));
    }

    @GetMapping("/patients/{patientId}/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<java.util.List<Referral>> listByPatient(@PathVariable Long patientId) {
        return Result.ok(referralRepository.findByPatientIdOrderByReferralDateDesc(patientId));
    }

    @PostMapping("/referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "referral", action = "CREATE")
    public Result<Referral> create(@RequestBody ReferralForm form) {
        Referral r = new Referral();
        r.setPatientId(form.getPatientId());
        r.setReferringDoctorId(form.getReferringDoctorId());
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
        return Result.ok(referralRepository.save(r));
    }

    @PutMapping("/referrals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "referral", action = "UPDATE")
    public Result<Referral> update(@PathVariable Long id, @RequestBody ReferralForm form) {
        Referral r = referralRepository.findById(id).orElseThrow();
        if (form.getStatus() != null) r.setStatus(form.getStatus());
        if (form.getAppointmentDate() != null) r.setAppointmentDate(form.getAppointmentDate());
        if (form.getCompletionDate() != null) r.setCompletionDate(form.getCompletionDate());
        if (form.getNotes() != null) r.setNotes(form.getNotes());
        return Result.ok(referralRepository.save(r));
    }

    @Data
    static class ReferralForm {
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
