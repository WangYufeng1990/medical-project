package com.example.medical.module.billing.service;

import com.example.medical.module.billing.dto.PriorAuthVO;
import com.example.medical.module.billing.repository.PriorAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriorAuthService {

    private final PriorAuthRepository priorAuthRepository;

    /** Prior authorizations of one patient. Visibility (doctor scope vs. the patient's own token) is the caller's concern. */
    public List<PriorAuthVO> listByPatient(Long patientId) {
        return priorAuthRepository.findByPatientIdOrderByRequestedAtDesc(patientId)
                .stream().map(PriorAuthVO::fromEntity).toList();
    }
}
