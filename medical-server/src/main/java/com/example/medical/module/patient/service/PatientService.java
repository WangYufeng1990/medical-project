package com.example.medical.module.patient.service;

import cn.hutool.core.util.StrUtil;
import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.dto.PatientFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    public Page<PatientVO> page(long page, long size, String keyword) {
        Specification<Patient> spec = (root, query, cb) -> {
            if (StrUtil.isBlank(keyword)) return null;
            String pattern = "%" + keyword + "%";
            // name, phoneMobile, email are encrypted — LIKE on ciphertext is not meaningful
            // Only MRN can be searched at the database level
            return cb.like(root.get("mrn"), pattern);
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return patientRepository.findAll(spec, pageable).map(PatientVO::fromEntity);
    }

    // Not cached — patient data contains PHI; Redis lacks field-level encryption
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

    @Transactional
    @Auditable(module = "patient", action = "DELETE")
    public void delete(Long id) {
        patientRepository.deleteById(id);
    }
}
