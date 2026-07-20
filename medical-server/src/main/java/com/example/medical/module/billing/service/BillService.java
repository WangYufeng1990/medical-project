package com.example.medical.module.billing.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.dto.BillFormDTO;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillRepository billRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    public Page<BillVO> page(long page, long size, String claimStatus, Long patientId) {
        Set<Long> scopedPatientIds = resolveDoctorScope();
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
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return billRepository.findAll(spec, pageable).map(this::toVO);
    }

    /**
     * Returns null for ADMIN (no filter), or a Set of patient IDs for DOCTOR
     * (patients they have appointments or prescriptions with).
     * Same pattern as ExportController.resolveExportScope().
     */
    private Set<Long> resolveDoctorScope() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) return null;
        if (user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            return null;
        Set<Long> ids = new HashSet<>();
        ids.addAll(appointmentRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        ids.addAll(prescriptionRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        return ids.isEmpty() ? null : ids;
    }

    public BillVO getById(Long id) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        return toVO(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "CREATE")
    public void create(BillFormDTO dto) {
        billRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "billing", action = "SUBMIT")
    public void submitClaim(Long id) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
        if (!"DRAFT".equals(b.getClaimStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Only draft bills can be submitted");
        }
        b.setClaimStatus("SUBMITTED");
        b.setClaimFilingDate(LocalDate.now());
        billRepository.save(b);
    }

    @Transactional
    @Auditable(module = "billing", action = "ADJUDICATE")
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

    @Transactional
    @Auditable(module = "billing", action = "PAY")
    public void pay(Long id, BigDecimal paymentAmount, String paymentMethod) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Bill not found"));
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
    @Auditable(module = "billing", action = "DENY")
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

    private BillVO toVO(Bill b) {
        String patientName = patientRepository.findById(b.getPatientId())
                .map(Patient::getName).orElse("");
        return BillVO.fromEntity(b, patientName);
    }
}
