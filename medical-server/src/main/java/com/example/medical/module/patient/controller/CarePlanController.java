package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.dto.CarePlanVO;
import com.example.medical.module.patient.entity.CarePlan;
import com.example.medical.module.patient.repository.CarePlanRepository;
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
public class CarePlanController {

    private final CarePlanRepository carePlanRepository;
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping("/patients/{patientId}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<CarePlanVO>> list(@PathVariable Long patientId, PageQuery pageQuery) {
        doctorPatientScope.requireAccess(patientId);
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "startDate"));
        var page = carePlanRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId), pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent().stream().map(CarePlanVO::fromEntity).toList()));
    }

    @PostMapping("/patients/{patientId}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "care_plan", action = "CREATE")
    public Result<CarePlanVO> create(@PathVariable Long patientId, @Valid @RequestBody CarePlanForm form) {
        doctorPatientScope.requireAccess(patientId);
        CarePlan cp = new CarePlan();
        cp.setPatientId(patientId);
        cp.setTitle(form.getTitle());
        cp.setGoal(form.getGoal());
        cp.setInterventions(form.getInterventions());
        cp.setStartDate(form.getStartDate() != null ? form.getStartDate() : LocalDate.now());
        cp.setTargetDate(form.getTargetDate());
        cp.setStatus("ACTIVE");
        cp.setCreatedBy(form.getCreatedBy());
        cp.setNotes(form.getNotes());
        return Result.ok(CarePlanVO.fromEntity(carePlanRepository.save(cp)));
    }

    @PutMapping("/patients/{patientId}/care-plans/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "care_plan", action = "UPDATE")
    public Result<CarePlanVO> update(@PathVariable Long patientId, @PathVariable Long id, @Valid @RequestBody CarePlanForm form) {
        doctorPatientScope.requireAccess(patientId);
        CarePlan cp = carePlanRepository.findById(id).orElseThrow();
        if (form.getStatus() != null) cp.setStatus(form.getStatus());
        if (form.getCompletedDate() != null) cp.setCompletedDate(form.getCompletedDate());
        if (form.getNotes() != null) cp.setNotes(form.getNotes());
        return Result.ok(CarePlanVO.fromEntity(carePlanRepository.save(cp)));
    }

    @Data
    static class CarePlanForm {
        private String title;
        private String goal;
        private String interventions;
        private LocalDate startDate;
        private LocalDate targetDate;
        private LocalDate completedDate;
        private String status;
        private Long createdBy;
        private String notes;
    }
}
