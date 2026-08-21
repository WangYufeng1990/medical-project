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
import com.example.medical.module.billing.entity.Charge;
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

@Service
@RequiredArgsConstructor
public class ChargeService {

    private final ChargeRepository chargeRepository;
    private final BillRepository billRepository;
    private final BillService billService;
    private final DoctorPatientScope doctorPatientScope;

    public Page<ChargeVO> list(Long patientId, PageQuery pageQuery) {
        Set<Long> scopedPatientIds = doctorPatientScope.resolve();
        var pageable = PageRequest.of((int) (pageQuery.getPage() - 1), (int) pageQuery.getSize(),
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
        c.setStatus("DRAFT");
        return ChargeVO.fromEntity(chargeRepository.save(c));
    }

    @Transactional
    @Auditable(module = "charge", action = "CONVERT_TO_BILL")
    public BillVO convert(Long id) {
        Charge c = chargeRepository.findById(id).orElseThrow();
        doctorPatientScope.requireAccess(c.getPatientId());
        if (!"DRAFT".equals(c.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Charge is not in DRAFT status");
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

        return billService.toVO(bill);
    }
}
