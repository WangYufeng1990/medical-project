package com.example.medical.common.security;

/**
 * Whether a staff account may take part in messaging at all: it exists, it is not
 * soft-deleted, and it is enabled.
 * <p>
 * Round 54.1 sends needed this as an authorization answer, so it cannot ride on
 * {@link com.example.medical.common.lookup.StaffLookup} — that interface is
 * deliberately display-only, and a name that resolves says nothing about whether
 * the account may be written to. The state stays where the account lives: the
 * system module implements this against its own repository.
 */
public interface StaffAccountState {

    /** true when the account exists, is not deleted, and is enabled. */
    boolean isActive(Long staffId);
}
