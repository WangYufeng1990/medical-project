package com.example.medical.common.lookup;

import java.util.Optional;

/**
 * A clinician's display name and signing identity, for modules that render a
 * doctor's name or sign a prescription but own no account record.
 * <p>
 * {@code AppointmentService}, {@code ChatService} and {@code PrescriptionService}
 * used to read {@code SysUserRepository} directly, which made three modules
 * depend on the system module's persistence — and, with the system module reading
 * patients, closed two import cycles through three modules.
 * <p>
 * Deliberately <b>scope-free and audit-free</b>, for the same reason as
 * {@link PatientLookup}: this answers "who is this id", not "may I see them".
 */
public interface StaffLookup {

    /** The account's display name; empty when there is no such account, or none recorded. */
    Optional<String> realName(Long userId);

    /** The identity a prescription is signed with; empty when there is no such account. */
    Optional<PrescriberIdentity> prescriberIdentity(Long userId);
}
