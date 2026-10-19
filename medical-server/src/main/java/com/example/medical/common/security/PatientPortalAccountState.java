package com.example.medical.common.security;

/**
 * Whether a patient record has a portal account that can receive a message: the
 * account exists, it is not soft-deleted, and it is enabled.
 * <p>
 * Round 54.1 records a message as delivered only when its receiver can actually
 * receive it. {@code PatientLookup.exists} answers a different question — that the
 * clinical record exists — and a record with no portal account (or a disabled or
 * deleted one) has no mailbox behind it.
 * <p>
 * Lockout is deliberately <b>not</b> consulted: a temporary failed-login lock is a
 * login state, not an account state, and a locked-out patient's mailbox is still
 * theirs. The answer comes from the patient module, which owns the credential row.
 */
public interface PatientPortalAccountState {

    /** true when the portal account exists, is not deleted, and is enabled. */
    boolean isActive(Long patientId);
}
