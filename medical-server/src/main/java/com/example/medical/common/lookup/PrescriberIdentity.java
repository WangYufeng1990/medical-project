package com.example.medical.common.lookup;

import java.util.Optional;

/**
 * The clinician identity a prescription is signed with. Server-derived from the
 * authenticated account, never from the client (Review III C5) — see
 * {@link StaffLookup#prescriberIdentity}.
 */
public record PrescriberIdentity(String npi, String deaNumber) {
}
