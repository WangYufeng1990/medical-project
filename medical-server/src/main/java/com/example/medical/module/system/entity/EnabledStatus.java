package com.example.medical.module.system.entity;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;

/**
 * The 0/1 flag behind {@code sys_user.status}, {@code sys_role.status} and
 * {@code sys_menu.status} — three tables, one vocabulary.
 *
 * <p>{@link #DISABLED} is what {@code AuthService} refuses to log in (403) and
 * what makes {@code SysUserService} set {@code force_logout_after} when an
 * account is switched off, so the two numbers are worth naming: written as bare
 * {@code 0}/{@code 1} they read as arbitrary, and a mistyped one silently locks
 * every account or none.
 */
public enum EnabledStatus {

    DISABLED(0),
    ENABLED(1);

    private final Integer value;

    EnabledStatus(Integer value) {
        this.value = value;
    }

    public Integer value() {
        return value;
    }

    public boolean matches(Integer other) {
        return value.equals(other);
    }

    /** The status for {@code value}, or null when it is missing or unrecognised. Exact. */
    public static EnabledStatus of(Integer value) {
        if (value == null) return null;
        for (EnabledStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        return null;
    }

    /**
     * Parses a client-supplied status and stores nothing but 0 or 1. An absent
     * value means {@link #ENABLED} — the same default the role and menu payloads
     * have always applied, while the user payload used to store null, which no
     * guard recognises as either state.
     */
    public static EnabledStatus parse(Integer value) {
        if (value == null) return ENABLED;
        EnabledStatus status = of(value);
        if (status == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Unknown status: " + value);
        }
        return status;
    }

    /** True only for {@link #ENABLED}: null is not an enabled account. */
    public static boolean isEnabled(Integer value) {
        return ENABLED.matches(value);
    }

    public static boolean isDisabled(Integer value) {
        return DISABLED.matches(value);
    }
}
