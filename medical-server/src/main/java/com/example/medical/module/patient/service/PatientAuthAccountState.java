package com.example.medical.module.patient.service;

import com.example.medical.common.security.PatientPortalAccountState;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Answers {@link PatientPortalAccountState} from the credential row.
 * <p>
 * {@code findByPatientId} is a JPQL query, so the entity's {@code is_deleted = 0}
 * restriction supplies the "not deleted" half; the status column is the same 0/1
 * vocabulary {@code PatientAuthSessionCheck} reads. Lockout is not part of the
 * answer — see the interface.
 */
@Component
@RequiredArgsConstructor
public class PatientAuthAccountState implements PatientPortalAccountState {

    private static final Integer ENABLED = 1;

    private final PatientAuthRepository patientAuthRepository;

    @Override
    public boolean isActive(Long patientId) {
        if (patientId == null) return false;
        return patientAuthRepository.findByPatientId(patientId)
                .map(auth -> ENABLED.equals(auth.getStatus()))
                .orElse(false);
    }
}
