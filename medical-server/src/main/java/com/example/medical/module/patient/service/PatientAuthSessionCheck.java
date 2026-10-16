package com.example.medical.module.patient.service;

import com.example.medical.common.security.PatientSessionCheck;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatientAuthSessionCheck implements PatientSessionCheck {

    /** What a token carries when the account's password has never been changed. */
    static final String NO_PASSWORD_CHANGE = "none";

    private final PatientAuthRepository patientAuthRepository;

    @Override
    public boolean isUsable(Long patientId, String issuedCredentialMarker) {
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
