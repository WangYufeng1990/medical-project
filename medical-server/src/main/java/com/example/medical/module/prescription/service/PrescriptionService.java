package com.example.medical.module.prescription.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.dto.PrescriptionFormDTO;
import com.example.medical.module.prescription.dto.PrescriptionItemVO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository sysUserRepository;
    private final CdsService cdsService;
    private final com.example.medical.module.prescription.repository.CdsOverrideRepository cdsOverrideRepository;
    private final DoctorPatientScope doctorPatientScope;

    public Page<PrescriptionVO> page(long page, long size) {
        Set<Long> scopedPatientIds = doctorPatientScope.resolve();
        Specification<Prescription> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (scopedPatientIds != null) {
                predicates = cb.and(predicates, root.get("patientId").in(scopedPatientIds));
            }
            return predicates;
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return prescriptionRepository.findAll(spec, pageable).map(this::toVO);
    }

    public PrescriptionVO getById(Long id) {
        Prescription p = prescriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Prescription not found"));
        doctorPatientScope.requireAccess(p.getPatientId());
        return toVO(p);
    }

    public List<PrescriptionVO> getByPatientId(Long patientId) {
        doctorPatientScope.requireAccess(patientId);
        return prescriptionRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "prescriptionDate"))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    @Auditable(module = "prescription", action = "CREATE", phiAccess = true)
    public void create(PrescriptionFormDTO dto, com.example.medical.security.LoginUser loginUser) {
        doctorPatientScope.requireAccess(dto.getPatientId());
        Prescription p = new Prescription();
        p.setPatientId(dto.getPatientId());
        // Prescriber identity is server-derived (Review III C5): doctorId, NPI
        // and DEA come from the authenticated user's profile, never the client.
        p.setDoctorId(loginUser.getUserId());
        com.example.medical.module.system.entity.SysUser prescriber =
                sysUserRepository.findById(loginUser.getUserId()).orElse(null);
        p.setPrescriberNpi(prescriber != null ? prescriber.getNpi() : null);
        p.setDeaNumber(prescriber != null ? prescriber.getDeaNumber() : null);
        p.setDiagnosis(dto.getDiagnosis());
        p.setIcd10Codes(dto.getIcd10Codes());
        p.setPrescriptionDate(dto.getPrescriptionDate() != null
                ? dto.getPrescriptionDate() : LocalDate.now());
        p.setPrescriptionType(dto.getPrescriptionType() != null
                ? dto.getPrescriptionType() : "MEDICATION");
        p.setRxStatus(dto.getRxStatus() != null ? dto.getRxStatus() : "active");
        p.setControlledSchedule(dto.getControlledSchedule());
        p.setPharmacyName(dto.getPharmacyName());
        p.setPharmacyPhone(dto.getPharmacyPhone());
        p.setPharmacyNpi(dto.getPharmacyNpi());

        // Build items in memory first so CDS runs BEFORE persistence
        // (Review III C1/H2 — previously the save happened first and warnings
        // were only logged after the fact).
        List<PrescriptionItem> items = new java.util.ArrayList<>();
        if (dto.getItems() != null) {
            for (var itemDTO : dto.getItems()) {
                items.add(itemDTO.toEntity());
            }
        }

        List<com.example.medical.module.prescription.dto.CdsWarning> warnings
                = new java.util.ArrayList<>();
        warnings.addAll(cdsService.checkDrugInteractions(items));
        warnings.addAll(cdsService.checkActiveMedicationInteractions(p.getPatientId(), items));
        warnings.addAll(cdsService.checkAllergyContraindications(p.getPatientId(), items));

        List<com.example.medical.module.prescription.dto.CdsWarning> blocking = warnings.stream()
                .filter(w -> "severe".equalsIgnoreCase(w.getSeverity())
                        || "contraindicated".equalsIgnoreCase(w.getSeverity()))
                .toList();
        if (!blocking.isEmpty()) {
            String summary = blocking.stream()
                    .map(w -> w.getType() + "[" + w.getSeverity() + "]: " + w.getDrugsInvolved())
                    .collect(java.util.stream.Collectors.joining("; "));
            if (dto.getOverrideReason() == null || dto.getOverrideReason().isBlank()) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "Prescription blocked by clinical decision support (" + summary
                        + "). Provide an override reason to proceed.");
            }
            log.warn("CDS override for prescription (patient={}): {}", p.getPatientId(), summary);
        }

        prescriptionRepository.save(p);
        for (PrescriptionItem item : items) {
            item.setPrescriptionId(p.getId());
            prescriptionItemRepository.save(item);
        }

        // Persist the override audit trail (Review III C2) — dead code before.
        for (com.example.medical.module.prescription.dto.CdsWarning w : blocking) {
            com.example.medical.module.prescription.entity.CdsOverride co =
                    new com.example.medical.module.prescription.entity.CdsOverride();
            co.setPrescriptionId(p.getId());
            co.setWarningType(w.getType());
            co.setSeverity(w.getSeverity());
            co.setDrugsInvolved(w.getDrugsInvolved() != null && w.getDrugsInvolved().length() > 200
                    ? w.getDrugsInvolved().substring(0, 197) + "..." : w.getDrugsInvolved());
            co.setOverrideReason(dto.getOverrideReason());
            co.setOverriddenBy(loginUser.getUserId());
            cdsOverrideRepository.save(co);
        }
    }

    @Transactional
    @Auditable(module = "prescription", action = "DELETE")
    public void delete(Long id) {
        prescriptionItemRepository.deleteByPrescriptionId(id);
        prescriptionRepository.deleteById(id);
    }

    @Transactional
    @Auditable(module = "prescription", action = "CANCEL")
    public void cancel(Long id) {
        Prescription p = prescriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Prescription not found"));
        doctorPatientScope.requireAccess(p.getPatientId());
        if (!"active".equals(p.getRxStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Only active prescriptions can be cancelled");
        }
        p.setRxStatus("cancelled");
        prescriptionRepository.save(p);
    }

    private PrescriptionVO toVO(Prescription p) {
        String patientName = patientRepository.findById(p.getPatientId())
                .map(Patient::getName).orElse("");
        String doctorName = sysUserRepository.findById(p.getDoctorId())
                .map(SysUser::getRealName).orElse("");
        List<PrescriptionItemVO> items = prescriptionItemRepository
                .findByPrescriptionId(p.getId())
                .stream().map(PrescriptionItemVO::fromEntity).toList();
        return PrescriptionVO.fromEntity(p, patientName, doctorName, items);
    }
}
