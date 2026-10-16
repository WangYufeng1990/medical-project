package com.example.medical.module.patient.service;

import com.example.medical.common.security.PatientSessionCheck;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatientAuthSessionCheck implements PatientSessionCheck {

    /** What a token carries when the account's password has never been changed. */
    static final String NO_PASSWORD_CHANGE = "none";

    private final PatientAuthRepository patientAuthRepository;
    private final PatientRepository patientRepository;

    /**
     * The credential row is not the whole answer: deleting a patient soft-deletes the
     * patient and leaves the credential behind, so a session has to be refused when
     * the record it belongs to is gone. {@code countById} runs under the entity's
     * {@code is_deleted = 0} restriction (52.4), which is what makes "deleted" answer
     * as absent here.
     */
    @Override
    public boolean isUsable(Long patientId, String issuedCredentialMarker) {
        if (patientRepository.countById(patientId) == 0) return false;
        return patientAuthRepository.findByPatientId(patientId)
                .map(auth -> Integer.valueOf(1).equals(auth.getStatus())
                        && markerOf(auth).equals(issuedCredentialMarker))
                .orElse(false);
    }

    /**
     * The marker a token issued now must carry. It is the moment the password last
     * changed, as the account stores it — both sides derive it the same way, so a
     * change in the same second as a login still distinguishes the two tokens.
     */
    public static String markerOf(PatientAuth auth) {
        return auth.getPasswordChangedAt() == null
                ? NO_PASSWORD_CHANGE : auth.getPasswordChangedAt().toString();
    }
}
