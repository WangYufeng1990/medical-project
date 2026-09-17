package com.example.medical.common.security;

import java.time.LocalDateTime;

/**
 * Answers "was this account revoked after a given moment?" for the token
 * validator.
 * <p>
 * {@code security.JwtClaimMapper} used to read {@code SysUserRepository}
 * directly, which made the authentication path depend on the {@code system}
 * module — and, since that module's controllers import
 * {@code security.LoginUser}, closed an import cycle. The account state stays
 * where the account lives.
 */
public interface AccountRevocationCheck {

    /** When the account was force-logged-out, or null if it never was. */
    LocalDateTime forceLogoutAfter(Long userId);
}
