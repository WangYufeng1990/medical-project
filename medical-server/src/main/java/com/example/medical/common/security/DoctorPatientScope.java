package com.example.medical.common.security;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * DOCTOR patient scoping (Post-Round 44 finding R2-2): a doctor may access
 * clinical/billing data only for patients they have appointments or
 * prescriptions with, plus the break-glass patient when an emergency token is
 * presented. ADMIN is unscoped.
 */
@Component
@RequiredArgsConstructor
public class DoctorPatientScope {

    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;

    /**
     * null = ADMIN, no filter. Otherwise the allowed patient-id set — possibly
     * EMPTY, which callers must treat as "no patients", not "no filter".
     */
    public Set<Long> resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) return null;

        // Role check via the Authentication authorities (ROLE_*), not
        // LoginUser.getAuthorities() — that one mirrors the token scopes.
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return null;

        Set<Long> ids = new HashSet<>();
        ids.addAll(appointmentRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        ids.addAll(prescriptionRepository.findDistinctPatientIdsByDoctor(user.getUserId()));
        if (user.getEmergencyPatientId() != null) ids.add(user.getEmergencyPatientId());
        return ids;
    }

    /** Throws FORBIDDEN when the patient is outside the current user's scope. */
    public void requireAccess(Long patientId) {
        Set<Long> scope = resolve();
        if (scope != null && !scope.contains(patientId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Access denied: patient outside your scope");
        }
    }
}
