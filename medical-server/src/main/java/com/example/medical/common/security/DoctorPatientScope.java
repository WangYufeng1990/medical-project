package com.example.medical.common.security;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DOCTOR patient scoping (Post-Round 44 finding R2-2): a doctor may access
 * clinical/billing data only for patients they have appointments or
 * prescriptions with, plus the break-glass patient when an emergency token is
 * presented. ADMIN is unscoped.
 * <p>
 * "Unscoped" is reserved for ADMIN alone. A missing {@code Authentication}, a
 * principal this application did not issue, or a principal without a staff role
 * used to fall through the same branch and read as ADMIN — an unidentified
 * caller got every patient in the practice. Those cases now fail closed
 * (Round 54.1); an authenticated patient is refused outright rather than
 * resolved through the doctor providers, which used to hand patient 2 the
 * patient list of doctor 2 because the two id spaces overlap.
 */
@Component
@RequiredArgsConstructor
public class DoctorPatientScope {

    private final List<DoctorPatientScopeProvider> providers;

    /**
     * null = ADMIN, no filter. Otherwise the allowed patient-id set — possibly
     * EMPTY, which callers must treat as "no patients", not "no filter".
     *
     * @throws BusinessException 401 without a verified identity, 403 for an
     *                           identity that is not ADMIN or DOCTOR
     */
    public Set<Long> resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser user)
                || user.getUserId() == null || user.getUserId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "Patient scope requires a verified identity");
        }

        // Role check via the Authentication authorities (ROLE_*), not
        // LoginUser.getAuthorities() — that one mirrors the token scopes.
        if (hasRole(auth, "ROLE_ADMIN")) return null;
        if (!hasRole(auth, "ROLE_DOCTOR")) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Patient scope is defined for staff identities only");
        }

        Set<Long> ids = new HashSet<>();
        for (DoctorPatientScopeProvider provider : providers) {
            ids.addAll(provider.patientIdsFor(user.getUserId()));
        }
        if (user.getEmergencyPatientId() != null) ids.add(user.getEmergencyPatientId());
        return ids;
    }

    /** Throws FORBIDDEN when the patient is outside the current user's scope. */
    public void requireAccess(Long patientId) {
        if (!permits(patientId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Access denied: patient outside your scope");
        }
    }

    /**
     * Whether the current identity may access the patient clinically — the same
     * rule as {@link #requireAccess} as a question rather than an exception.
     * <p>
     * Read-only for callers that need to combine it with a rule of their own.
     * Messaging uses it that way (Round 54.1) and never the other way round: no
     * messaging relationship is ever merged into this scope, so a conversation
     * cannot widen what clinical reads are allowed.
     */
    public boolean permits(Long patientId) {
        Set<Long> scope = resolve();
        return scope == null || scope.contains(patientId);
    }

    /**
     * Whether a named staff account is related to the patient through one of the
     * provider modules' records. Answers "may this account be reached about this
     * patient", not "who is this id" — a display-name lookup is not authorization
     * (Round 54.1); this asks the same relationship {@link #resolve()} uses.
     */
    public boolean isRelated(Long staffId, Long patientId) {
        if (staffId == null || patientId == null) return false;
        for (DoctorPatientScopeProvider provider : providers) {
            if (provider.patientIdsFor(staffId).contains(patientId)) return true;
        }
        return false;
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (role.equals(authority.getAuthority())) return true;
        }
        return false;
    }
}
