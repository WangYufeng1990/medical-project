package com.example.medical.module.system.service;

import com.example.medical.common.security.StaffAccountState;
import com.example.medical.module.system.entity.EnabledStatus;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers {@link StaffAccountState} from the account's own table. */
@Component
@RequiredArgsConstructor
public class SysUserAccountState implements StaffAccountState {

    private final SysUserRepository sysUserRepository;

    @Override
    public boolean isActive(Long staffId) {
        if (staffId == null) return false;
        // @SQLRestriction on SysUser excludes a soft-deleted account, so this finds
        // nothing once the row is deleted — "exists, not deleted, enabled" in one look.
        return sysUserRepository.findById(staffId)
                .map(user -> EnabledStatus.isEnabled(user.getStatus()))
                .orElse(false);
    }
}
