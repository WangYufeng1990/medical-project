package com.example.medical.module.billing.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.PageQuery;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.dto.ChargeForm;
import com.example.medical.module.billing.dto.ChargeVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.entity.BillClaimStatus;
import com.example.medical.module.billing.entity.Charge;
import com.example.medical.module.billing.entity.ChargeStatus;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.billing.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import com.example.medical.common.base.Pages;

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final DoctorPatientScope doctorPatientScope;

    public Page<ChargeVO> list(Long patientId, PageQuery pageQuery) {
        Set<Long> scopedPatientIds = doctorPatientScope.resolve();
        var pageable = Pages.of(pageQuery.getPage(), pageQuery.getSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<Charge> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (patientId != null) predicates = cb.and(predicates, cb.equal(root.get("patientId"), patientId));
            if (scopedPatientIds != null) {
                predicates = cb.and(predicates, root.get("patientId").in(scopedPatientIds));
            }
            return predicates;
        };
        return chargeRepository.findAll(spec, pageable).map(ChargeVO::fromEntity);
    }

    @Transactional
    @Auditable(module = "charge", action = "CREATE", phiAccess = true)
    public ChargeVO create(ChargeForm form) {
        Charge c = form.toEntity();
        c.setStatus(ChargeStatus.DRAFT.value());
        return ChargeVO.fromEntity(chargeRepository.save(c));
    }

    /**
     * Whether the visit already produced a charge. The appointment module asks
     * before completing a visit, because a charge can also be entered by hand for
     * the same appointment — but only this module knows what "already charged"
     * means.
     */
    public boolean visitAlreadyCharged(Long appointmentId, Long patientId) {
        return chargeRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("appointmentId"), appointmentId),
                        cb.equal(root.get("patientId"), patientId)),
                PageRequest.of(0, 1)).hasContent();
    }

    /**
     * The charge a completed visit produces. Called by the appointment module
     * (52.3b) instead of it building a {@code Charge} through this module's
     * repository: the amount rule, the DRAFT status and the audit row are the
     * owning module's, and the appointment path used to skip the audit row
     * entirely.
     *
     * @param cptCodes   the visit's CPT code, which the amount rule reads
     * @param icd10Codes the diagnosis recorded on the visit
     */
    @Transactional
    @Auditable(module = "charge", action = "CREATE", phiAccess = true)
    public void createForVisit(Long appointmentId, Long patientId, Long doctorId,
                               String cptCodes, String icd10Codes, String visitType) {
        Charge c = new Charge();
        c.setPatientId(patientId);
        c.setAppointmentId(appointmentId);
        c.setDoctorId(doctorId);
        c.setCptCodes(cptCodes);
        c.setIcd10Codes(icd10Codes);
        c.setVisitType(visitType);
        c.setChargeAmount(amountFor(cptCodes));
        c.setStatus(ChargeStatus.DRAFT.value());
        chargeRepository.save(c);
    }

    /** An office visit (992xx) bills 90, anything else 100 — the rule the appointment module used to hold. */
    private BigDecimal amountFor(String cptCodes) {
        return cptCodes != null && cptCodes.startsWith("992")
                ? new BigDecimal("90") : new BigDecimal("100");
    }

    @Transactional
    @Auditable(module = "charge", action = "CONVERT_TO_BILL")
    public BillVO convert(Long id) {
        Charge c = chargeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Charge not found"));
        doctorPatientScope.requireAccess(c.getPatientId());
        if (!ChargeStatus.isConvertible(c.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Charge is not in " + ChargeStatus.DRAFT.value() + " status");
        }
        // A visit can only be billed once. Converting a charge whose appointment
        // already carries a bill used to create a second bill for the same visit
        // — and dropped the appointment link on the way.
        if (c.getAppointmentId() != null) {
            Bill existing = billRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("appointmentId"), c.getAppointmentId()),
                    PageRequest.of(0, 1)).getContent().stream().findFirst().orElse(null);
            if (existing != null) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "Appointment " + c.getAppointmentId() + " is already billed (bill " + existing.getId() + ")");
            }
        }
        Bill bill = new Bill();
        bill.setPatientId(c.getPatientId());
        bill.setAppointmentId(c.getAppointmentId());
        bill.setCptCodes(c.getCptCodes());
        bill.setIcd10Codes(c.getIcd10Codes());
        bill.setTotalCharge(c.getChargeAmount());
        bill.setCopayAmount(BigDecimal.ZERO);
        bill.setBillType("PROFESSIONAL");
        bill.setClaimStatus(BillClaimStatus.DRAFT.value());
        bill = billRepository.save(bill);

        c.setStatus(ChargeStatus.BILLED.value());
        c.setBillId(bill.getId());
        chargeRepository.save(c);

        return billService.toVO(bill);
    }
}
