package com.example.medical.common.security;

/**
 * Whether a patient token is still usable, for the token validator.
 * <p>
 * Patient tokens carry a {@code patient} id in {@code uid}, and that id is
 * deliberately never checked against {@code sys_user} revocation — the two tables'
 * ids overlap, so a patient would answer for a staff account (Review 44 R2-1). The
 * consequence was that nothing checked patient tokens at all: an access token kept
 * working after the password changed or the account was disabled, and the refresh
 * token kept minting replacements for its full 30-day life. This is the patient-side
 * counterpart of {@link AccountRevocationCheck}, in its own identity domain.
 * <p>
 * The token carries the credential marker it was issued against
 * ({@link #CREDENTIAL_MARKER_CLAIM}); comparing markers is an equality test, not a
 * timestamp comparison, so a password changed in the same second as a login cannot
 * invalidate the session that login just created.
 */
public interface PatientSessionCheck {

    /** The claim carrying the credential marker a token was issued against. */
    String CREDENTIAL_MARKER_CLAIM = "pwd";

    /** true when the account exists, is enabled, and its password has not changed since this marker. */
    boolean isUsable(Long patientId, String issuedCredentialMarker);
}
