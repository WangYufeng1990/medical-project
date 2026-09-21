package com.example.medical.module.patient.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.dto.PatientFormDTO;
import com.example.medical.module.patient.dto.PatientSelfUpdateFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.medical.common.base.Pages;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    /**
     * @param scopedPatientIds doctor-scoped patient ids (null = ADMIN, no filter);
     *                         empty set = no patients visible (Review III C3)
     */
    public Page<PatientVO> page(long page, long size, String keyword, java.util.Set<Long> scopedPatientIds) {
        Specification<Patient> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword + "%";
                // name, phoneMobile, email are encrypted — LIKE on ciphertext is not meaningful
                // Only MRN can be searched at the database level
                predicates = cb.and(predicates, cb.like(root.get("mrn"), pattern));
            }
            if (scopedPatientIds != null) {
                predicates = cb.and(predicates, root.get("id").in(scopedPatientIds));
            }
            return predicates;
        };
        Pageable pageable = Pages.of(page, size);
        return patientRepository.findAll(spec, pageable).map(PatientVO::fromEntity);
    }

    /**
     * Paged read for the CSV export loop. Not routed through {@code Pages}: the
     * export walks the table itself with its own 500-row page size, and the size
     * is chosen by the caller. An empty doctor scope means no rows — never "no
     * filter".
     */
    public Page<PatientVO> exportPage(long page, long size, Set<Long> scopedPatientIds) {
        Pageable pageable = PageRequest.of((int) page, (int) size, Sort.by(Sort.Direction.DESC, "createTime"));
        if (scopedPatientIds != null && scopedPatientIds.isEmpty()) return Page.empty(pageable);
        Specification<Patient> spec = scopedPatientIds == null
                ? null
                : (root, query, cb) -> root.get("id").in(scopedPatientIds);
        return patientRepository.findAll(spec, pageable).map(PatientVO::fromEntity);
    }

    // Not cached — patient data contains PHI; Redis lacks field-level encryption
    @Auditable(module = "patient", action = "VIEW", phiAccess = true)
    public PatientVO getById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));
        return PatientVO.fromEntity(patient);
    }

    @Transactional
    @Auditable(module = "patient", action = "CREATE", phiAccess = true)
    public void create(PatientFormDTO dto) {
        patientRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "patient", action = "UPDATE", phiAccess = true)
    public void update(Long id, PatientFormDTO dto) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));
        dto.applyTo(patient);
        patientRepository.save(patient);
    }

    /**
     * The portal's own profile read. Not audited and returns {@code null} when the
     * patient row is gone: a patient reading their own record is the baseline
     * case, and adding a row per page load would bury the real accesses in
     * {@code /patient/me/disclosures}.
     */
    public PatientVO profile(Long patientId) {
        return patientRepository.findById(patientId).map(PatientVO::fromEntity).orElse(null);
    }

    @Transactional
    @Auditable(module = "patient", action = "UPDATE_PROFILE", phiAccess = true)
    public void updateOwnProfile(Long patientId, PatientSelfUpdateFormDTO form) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));
        // Staff-verified fields (name, MRN, DOB, sex at birth, insurance, allergies)
        // are not part of the payload, so they cannot be changed from here at all.
        form.applyTo(patient);
        patientRepository.save(patient);
    }

    @Transactional
    @Auditable(module = "patient", action = "DELETE")
    public void delete(Long id) {
        patientRepository.deleteById(id);
    }
}
