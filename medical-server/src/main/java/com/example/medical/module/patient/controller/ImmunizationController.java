package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.ImmunizationVO;
import com.example.medical.module.patient.entity.Immunization;
import com.example.medical.module.patient.repository.ImmunizationRepository;
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
public class ImmunizationController {

    private final ImmunizationRepository immunizationRepository;

    @GetMapping("/patients/{patientId}/immunizations")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<ImmunizationVO>> list(@PathVariable Long patientId, PageQuery pageQuery) {
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "administrationDate"));
        var page = immunizationRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId), pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent().stream().map(ImmunizationVO::fromEntity).toList()));
    }

    @PostMapping("/patients/{patientId}/immunizations")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "immunization", action = "CREATE")
    public Result<ImmunizationVO> create(@PathVariable Long patientId, @Valid @RequestBody ImmunizationForm form) {
        Immunization imm = new Immunization();
        imm.setPatientId(patientId);
        imm.setVaccineName(form.getVaccineName());
        imm.setCvxCode(form.getCvxCode());
        imm.setAdministrationDate(form.getAdministrationDate() != null ? form.getAdministrationDate() : LocalDate.now());
        imm.setLotNumber(form.getLotNumber());
        imm.setManufacturer(form.getManufacturer());
        imm.setDoseNumber(form.getDoseNumber());
        imm.setSite(form.getSite());
        imm.setRoute(form.getRoute());
        imm.setStatus(form.getStatus() != null ? form.getStatus() : "completed");
        imm.setAdministeredBy(form.getAdministeredBy());
        imm.setNotes(form.getNotes());
        return Result.ok(ImmunizationVO.fromEntity(immunizationRepository.save(imm)));
    }

    @Data
    static class ImmunizationForm {
        private String vaccineName;
        private String cvxCode;
        private LocalDate administrationDate;
        private String lotNumber;
        private String manufacturer;
        private String doseNumber;
        private String site;
        private String route;
        private String status;
        private Long administeredBy;
        private String notes;
    }
}
