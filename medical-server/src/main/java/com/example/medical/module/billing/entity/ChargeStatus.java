package com.example.medical.module.billing.entity;

/**
 * The charge lifecycle: a charge is captured as {@link #DRAFT} and becomes
 * {@link #BILLED} when it is converted into a bill — the only two values this
 * column has ever held.
 *
 * <p>Unlike {@link BillClaimStatus} and the prescription statuses, no client
 * ever supplies this value: creation always writes {@code DRAFT} and the
 * convert transition owns the other one. There is therefore no {@code parse()}
 * here — an unused parser would be dead code, not symmetry.
 */
public enum ChargeStatus {

    DRAFT("DRAFT"),
    BILLED("BILLED");

    private final String value;

    ChargeStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean matches(String other) {
        return value.equals(other);
    }

    /** The status for {@code value}, or null when it is missing or unrecognised. Exact. */
    public static ChargeStatus of(String value) {
        if (value == null) return null;
        for (ChargeStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        return null;
    }

    /** True when a charge in this status may still be turned into a bill. */
    public static boolean isConvertible(String status) {
        return DRAFT.matches(status);
    }
}
