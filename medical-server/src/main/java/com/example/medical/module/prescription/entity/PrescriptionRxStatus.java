package com.example.medical.module.prescription.entity;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;

import java.util.Locale;

/**
 * Prescription lifecycle, and the single place these four strings are written
 * down. They are lower case (the claim statuses are upper case) and that is the
 * vocabulary already in the data, so the strings stay exactly as they were.
 * <p>
 * Only ACTIVE is actionable: transmitting, cancelling and requesting a refill all
 * require it, and {@code CANCELLED}/{@code COMPLETED} are where a prescription
 * stops. Nothing in this codebase writes COMPLETED — it comes from seeded data and
 * from whatever the database already holds, which is precisely why it has to be a
 * named value rather than an unknown string.
 * <p>
 * The REST payload keeps sending these strings, so nothing changes on the wire.
 */
public enum PrescriptionRxStatus {

    ACTIVE("active"),
    GENERATED("generated"),
    CANCELLED("cancelled"),
    COMPLETED("completed");

    private final String value;

    PrescriptionRxStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** True when {@code value} is this status. Null-safe. */
    public boolean matches(String value) {
        return value != null && value.equals(this.value);
    }

    /** The only status a prescription may be transmitted, cancelled or refilled from. */
    public static boolean isActive(String value) {
        return ACTIVE.matches(value);
    }

    /** No writer moves a prescription out of these two. */
    public static boolean isTerminal(String value) {
        return CANCELLED.matches(value) || COMPLETED.matches(value);
    }

    /**
     * The status for {@code value}, or null when it is missing or unrecognised.
     * Exact: this is what stored values are compared against.
     */
    public static PrescriptionRxStatus of(String value) {
        if (value == null) return null;
        for (PrescriptionRxStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        return null;
    }

    /**
     * Parses a client-supplied status and stores nothing but the canonical value.
     * Lenient about case and surrounding space, and a 400 for anything else: this
     * path used to store whatever it was sent, so a typo — or a value from a client
     * that guessed the vocabulary — became a row no guard could ever match.
     */
    public static PrescriptionRxStatus parse(String value) {
        PrescriptionRxStatus status = value == null ? null : of(value.trim().toLowerCase(Locale.ROOT));
        if (status == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Unknown prescription status: " + value);
        }
        return status;
    }
}
