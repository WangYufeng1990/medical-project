package com.example.medical.module.billing.entity;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;

/**
 * Insurance-claim lifecycle, and the single place these five strings are written
 * down. Every one of them used to be a literal scattered across {@code BillService},
 * {@code ChargeService}, the dashboard's raw SQL and the controller's filter
 * parameter — which is how the UI ended up offering "Pay Now" for a DRAFT bill
 * while the service accepted only PENDING.
 * <p>
 * The column stays {@code VARCHAR} and the REST payload keeps sending the same
 * strings, so nothing changes on the wire.
 */
public enum BillClaimStatus {

    DRAFT("DRAFT"),
    SUBMITTED("SUBMITTED"),
    PENDING("PENDING"),
    PAID("PAID"),
    DENIED("DENIED");

    private final String value;

    BillClaimStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** True when {@code value} is this status. Null-safe. */
    public boolean matches(String value) {
        return value != null && value.equals(this.value);
    }

    public static boolean anyOf(String value, BillClaimStatus... statuses) {
        for (BillClaimStatus status : statuses) {
            if (status.matches(value)) return true;
        }
        return false;
    }

    /** An adjudicated claim: paid or denied. */
    public static boolean isTerminal(String value) {
        return anyOf(value, PAID, DENIED);
    }

    /** Whether a payment may be recorded — the rule the billing UI kept getting wrong. */
    public static boolean isPayable(String value) {
        return PENDING.matches(value);
    }

    /** The status for {@code value}, or null when it is missing or unrecognised. */
    public static BillClaimStatus of(String value) {
        if (value == null) return null;
        for (BillClaimStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        return null;
    }

    /** Parses a client-supplied status, rejecting anything unknown with a 400. */
    public static BillClaimStatus parse(String value) {
        BillClaimStatus status = of(value);
        if (status == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Unknown claim status: " + value);
        }
        return status;
    }
}
