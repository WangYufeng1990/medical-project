package com.example.medical.common.config;

import com.example.medical.common.audit.KeyAuditRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeyAuditBridge {

    private final KeyAuditRepository keyAuditRepository;

    @PostConstruct
    void bridge() {
        AesCryptoUtil.setKeyAuditRepository(keyAuditRepository);
    }
}
