package com.example.medical.common.config;

import com.example.medical.common.audit.KeyAuditRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Wires the audit repository into {@link AesCryptoUtil} and then runs its startup
 * audit. Both steps live here rather than in {@code AesCryptoUtil.init()} because
 * this bean is created once its dependencies exist: a {@code @PostConstruct} in the
 * crypto utility ran first, saw a null repository, and silently skipped the
 * KEY_INIT audit and the runtime-rotation mismatch check.
 */
@Component
@RequiredArgsConstructor
public class KeyAuditBridge {

    private final KeyAuditRepository keyAuditRepository;
    private final AesCryptoUtil aesCryptoUtil;

    @PostConstruct
    void bridge() {
        AesCryptoUtil.setKeyAuditRepository(keyAuditRepository);
        aesCryptoUtil.recordStartupAndCheckRotation();
    }
}
