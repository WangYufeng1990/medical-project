package com.example.medical.module.billing.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.entity.Charge;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.billing.repository.ChargeRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/charges")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
public class ChargeController {

    private final ChargeRepository chargeRepository;
    private final BillRepository billRepository;

    @GetMapping
    public Result<PageResult<Charge>> list(@RequestParam(required = false) Long patientId, PageQuery pageQuery) {
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        org.springframework.data.jpa.domain.Specification<Charge> spec = patientId != null
                ? (root, query, cb) -> cb.equal(root.get("patientId"), patientId)
                : null;
        var page = chargeRepository.findAll(spec, pageable);
        return Result.ok(PageResult.of(page.getTotalElements(), page.getSize(),
                page.getNumber() + 1, page.getContent()));
    }

    @PostMapping
    @Transactional
    @Auditable(module = "charge", action = "CREATE")
    public Result<Charge> create(@RequestBody ChargeForm form) {
        Charge c = new Charge();
        c.setPatientId(form.getPatientId());
        c.setAppointmentId(form.getAppointmentId());
        c.setDoctorId(form.getDoctorId());
        c.setCptCodes(form.getCptCodes());
        c.setIcd10Codes(form.getIcd10Codes());
        c.setUnits(form.getUnits() != null ? form.getUnits() : 1);
        c.setChargeAmount(form.getChargeAmount());
        c.setVisitType(form.getVisitType());
        c.setStatus("DRAFT");
        c.setNotes(form.getNotes());
        return Result.ok(chargeRepository.save(c));
    }

    @PutMapping("/{id}/convert")
    @Transactional
    @Auditable(module = "charge", action = "CONVERT_TO_BILL")
    public Result<Bill> convert(@PathVariable Long id) {
        Charge c = chargeRepository.findById(id).orElseThrow();
        if (!"DRAFT".equals(c.getStatus())) {
            return Result.fail(409, "Charge is not in DRAFT status");
        }
        Bill bill = new Bill();
        bill.setPatientId(c.getPatientId());
        bill.setCptCodes(c.getCptCodes());
        bill.setIcd10Codes(c.getIcd10Codes());
        bill.setTotalCharge(c.getChargeAmount());
        bill.setCopayAmount(BigDecimal.ZERO);
        bill.setBillType("PROFESSIONAL");
        bill.setClaimStatus("DRAFT");
        bill = billRepository.save(bill);

        c.setStatus("BILLED");
        c.setBillId(bill.getId());
        chargeRepository.save(c);

        return Result.ok(bill);
    }

    @Data
    static class ChargeForm {
        private Long patientId;
        private Long appointmentId;
        private Long doctorId;
        private String cptCodes;
        private String icd10Codes;
        private Integer units;
        private BigDecimal chargeAmount;
        private String visitType;
        private String notes;
    }
}
