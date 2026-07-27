package com.example.medical.module.billing.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.entity.PriorAuth;
import com.example.medical.module.billing.repository.PriorAuthRepository;
import com.example.medical.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PriorAuthController {

    private final PriorAuthRepository priorAuthRepository;

    @GetMapping("/prior-auths")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<PriorAuth>> list(@RequestParam(required = false) Long patientId, PageQuery pageQuery) {
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "requestedAt"));
        var spec = patientId != null
                ? (org.springframework.data.jpa.domain.Specification<PriorAuth>) (root, query, cb) -> cb.equal(root.get("patientId"), patientId)
                : null;
        var page = priorAuthRepository.findAll(spec, pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent()));
    }

    @PostMapping("/prior-auths")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "prior_auth", action = "CREATE")
    public Result<PriorAuth> create(@RequestBody PriorAuthForm form, @AuthenticationPrincipal LoginUser loginUser) {
        PriorAuth pa = new PriorAuth();
        pa.setPatientId(form.getPatientId());
        pa.setAuthType(form.getAuthType());
        pa.setItemName(form.getItemName());
        pa.setItemCode(form.getItemCode());
        pa.setInsurancePayer(form.getInsurancePayer());
        pa.setStatus("PENDING");
        pa.setRequestedAt(LocalDate.now());
        pa.setRequestedBy(loginUser.getUserId());
        pa.setNotes(form.getNotes());
        return Result.ok(priorAuthRepository.save(pa));
    }

    @PutMapping("/prior-auths/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @Transactional
    @Auditable(module = "prior_auth", action = "UPDATE")
    public Result<PriorAuth> update(@PathVariable Long id, @RequestBody PriorAuthForm form) {
        PriorAuth pa = priorAuthRepository.findById(id).orElseThrow();
        if (form.getStatus() != null) pa.setStatus(form.getStatus());
        if (form.getAuthNumber() != null) pa.setAuthNumber(form.getAuthNumber());
        if (form.getResolvedAt() != null) pa.setResolvedAt(form.getResolvedAt());
        if (form.getNotes() != null) pa.setNotes(form.getNotes());
        return Result.ok(priorAuthRepository.save(pa));
    }

    @Data
    static class PriorAuthForm {
        private Long patientId;
        private String authType;
        private String itemName;
        private String itemCode;
        private String insurancePayer;
        private String status;
        private LocalDate resolvedAt;
        private String authNumber;
        private String notes;
    }
}
