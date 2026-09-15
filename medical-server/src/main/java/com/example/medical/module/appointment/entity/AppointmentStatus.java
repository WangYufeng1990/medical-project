package com.example.medical.module.appointment.entity;

/**
 * Appointment lifecycle, and the single place these codes are written down.
 * <p>
 * Only two of them were ever documented anywhere: 4 is set by
 * {@code AppointmentScheduler} when a scheduled visit passes, and 2 is excluded
 * by the conflict query. The rest were inferred from the code.
 * <p>
 * The database column stays {@code INT} and the REST payload keeps sending the
 * numeric code, so this enum changes nothing on the wire — it exists so that
 * {@code status == 3} stops being an unnamed number in the middle of business
 * rules.
 */
public enum AppointmentStatus {

    SCHEDULED(0),
    ARRIVED(1),
    CANCELLED(2),
    COMPLETED(3),
    NO_SHOW(4);

    private final int code;

    AppointmentStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /** True when {@code code} is this status. Null-safe. */
    public boolean matches(Integer code) {
        return code != null && code == this.code;
    }

    public static boolean anyOf(Integer code, AppointmentStatus... statuses) {
        if (code == null) return false;
        for (AppointmentStatus status : statuses) {
            if (status.matches(code)) return true;
        }
        return false;
    }

    /** A closed appointment: cancelled, completed or no-show. */
    public static boolean isTerminal(Integer code) {
        return anyOf(code, CANCELLED, COMPLETED, NO_SHOW);
    }

    /** The status for {@code code}, or null when it is missing or unrecognised. */
    public static AppointmentStatus fromCode(Integer code) {
        if (code == null) return null;
        for (AppointmentStatus status : values()) {
            if (status.code == code) return status;
        }
        return null;
    }
}
