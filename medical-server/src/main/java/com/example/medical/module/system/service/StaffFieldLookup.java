package com.example.medical.module.system.service;

import com.example.medical.common.lookup.PrescriberIdentity;
import com.example.medical.common.lookup.StaffLookup;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StaffFieldLookup implements StaffLookup {

    private final SysUserRepository sysUserRepository;

    @Override
    public Optional<String> realName(Long userId) {
        return sysUserRepository.findRealNameById(userId);
    }

    @Override
    public Optional<PrescriberIdentity> prescriberIdentity(Long userId) {
        return sysUserRepository.findPrescriberIdentityById(userId);
    }
}
