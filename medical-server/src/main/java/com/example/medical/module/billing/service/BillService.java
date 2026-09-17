package com.example.medical.module.billing.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.billing.dto.BillFormDTO;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.example.medical.common.base.Pages;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final PatientRepository patientRepository;
    private final DoctorPatientScope doctorPatientScope;

    public Page<BillVO> page(long page, long size, String claimStatus, Long patientId) {
        Set<Long> scopedPatientIds = doctorPatientScope.resolve();
        Specification<Bill> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (claimStatus != null) {
                predicates = cb.and(predicates, cb.equal(root.get("claimStatus"), claimStatus));
            }
            if (patientId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("patientId"), patientId));
            }
            if (scopedPatientIds != null) {
                predicates = cb.and(predicates, root.get("patientId").in(scopedPatientIds));
            }
            return predicates;
        };
        Pageable pageable = Pages.of(page, size);
        return billRepository.findAll(spec, pageable).map(this::toVO);
    }

    public BillVO toVO(Bill b) {
        String patientName = patientRepository.findById(b.getPatientId())
                .map(Patient::getName).orElse("");
        return BillVO.fromEntity(b, patientName);
    }

    public BillVO getById(Long id) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        doctorPatientScope.requireAccess(b.getPatientId());
        return toVO(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "CREATE", phiAccess = true)
    public void create(BillFormDTO dto) {
        billRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "billing", action = "SUBMIT")
    public void submitClaim(Long id) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        doctorPatientScope.requireAccess(b.getPatientId());
        if (!"DRAFT".equals(b.getClaimStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Only draft bills can be submitted");
        }
        b.setClaimStatus("SUBMITTED");
        b.setClaimFilingDate(LocalDate.now());
        billRepository.save(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "ADJUDICATE", phiAccess = true)
    public void adjudicate(Long id, BigDecimal adjustment, BigDecimal insurancePayment,
                           String claimNumber, LocalDate adjudicationDate) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        if (!"SUBMITTED".equals(b.getClaimStatus()) && !"PENDING".equals(b.getClaimStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Bill must be submitted or pending to adjudicate");
        }

        BigDecimal total = b.getTotalCharge() != null ? b.getTotalCharge() : BigDecimal.ZERO;
        BigDecimal adj = adjustment != null ? adjustment : BigDecimal.ZERO;
        BigDecimal insPay = insurancePayment != null ? insurancePayment : BigDecimal.ZERO;
        BigDecimal patientResp = total.subtract(adj).subtract(insPay);
        if (patientResp.compareTo(BigDecimal.ZERO) < 0) patientResp = BigDecimal.ZERO;

        b.setInsuranceAdjustment(adj);
        b.setInsurancePayment(insPay);
        b.setPatientResponsibility(patientResp);
        b.setInsuranceClaimNumber(claimNumber);
        b.setAdjudicationDate(adjudicationDate != null ? adjudicationDate : LocalDate.now());
        b.setClaimStatus(patientResp.compareTo(BigDecimal.ZERO) > 0 ? "PENDING" : "PAID");
        billRepository.save(b);
    }

    /** The patient portal's own view: no doctor scope, the id comes from the token. */
    public Page<BillVO> pageForPatient(Long patientId, long page, long size) {
        Pageable pageable = Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        return billRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                pageable).map(this::toVO);
    }

    /** Unpaged patient-scoped read, for the HIPAA export. */
    public List<BillVO> listForPatient(Long patientId) {
        return billRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "createTime"))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    @Auditable(module = "billing", action = "PAY", phiAccess = true)
    public void pay(Long id, BigDecimal paymentAmount, String paymentMethod) {
        applyPayment(findBill(id), paymentAmount, paymentMethod);
    }

    /**
     * Patient-initiated payment of their own bill. Ownership is checked here so
     * this entry point cannot be used against another patient's bill.
     */
    @Transactional
    @Auditable(module = "billing", action = "PAY", phiAccess = true)
    public void payByPatient(Long id, Long patientId, BigDecimal paymentAmount, String paymentMethod) {
        Bill b = findBill(id);
        if (!b.getPatientId().equals(patientId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Access denied");
        }
        applyPayment(b, paymentAmount, paymentMethod);
    }

    private Bill findBill(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
    }

    private void applyPayment(Bill b, BigDecimal paymentAmount, String paymentMethod) {
        if (!"PENDING".equals(b.getClaimStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Bill must be in PENDING state to accept payment");
        }

        BigDecimal paid = b.getPatientPaidAmount() != null ? b.getPatientPaidAmount() : BigDecimal.ZERO;
        BigDecimal responsibility = b.getPatientResponsibility() != null
                ? b.getPatientResponsibility() : b.getTotalCharge();
        BigDecimal newPaid = paid.add(paymentAmount);
        b.setPatientPaidAmount(newPaid);
        b.setPayTime(LocalDateTime.now());
        b.setPaymentMethod(paymentMethod);

        if (newPaid.compareTo(responsibility) >= 0) {
            b.setClaimStatus("PAID");
        }
        billRepository.save(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "DENY", phiAccess = true)
    public void denyClaim(Long id, String reason) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        if ("PAID".equals(b.getClaimStatus()) || "DENIED".equals(b.getClaimStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Cannot deny a bill that is already paid or denied");
        }
        b.setClaimStatus("DENIED");
        b.setAdjudicationDate(LocalDate.now());
        billRepository.save(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "DELETE")
    public void delete(Long id) {
        billRepository.deleteById(id);
    }
}
