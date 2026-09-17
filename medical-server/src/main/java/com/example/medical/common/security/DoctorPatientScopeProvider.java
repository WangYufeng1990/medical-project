package com.example.medical.common.security;

import java.util.Collection;

/**
 * Contributes the patients a doctor is related to through one module's records.
 * <p>
 * {@link DoctorPatientScope} used to query the appointment and prescription
 * repositories directly, which made {@code common} depend on two business
 * modules — and, since those modules' services inject {@code DoctorPatientScope},
 * closed an import cycle. Each module now answers for its own records.
 */
public interface DoctorPatientScopeProvider {

    /** Patient ids this doctor has a relationship with; empty when there are none. */
    Collection<Long> patientIdsFor(Long doctorId);
}
