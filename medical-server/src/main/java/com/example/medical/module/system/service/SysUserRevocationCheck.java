package com.example.medical.module.system.service;

import com.example.medical.common.security.AccountRevocationCheck;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SysUserRevocationCheck implements AccountRevocationCheck {

    private final SysUserRepository sysUserRepository;

    @Override
    public LocalDateTime forceLogoutAfter(Long userId) {
        return sysUserRepository.findForceLogoutAfterByUserId(userId);
    }
}
