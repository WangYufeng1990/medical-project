package com.example.medical.module.system.dto;

import com.example.medical.common.annotation.PhiField;
import com.example.medical.module.system.entity.SysUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysUserVO {

    private Long id;
    private String username;
    private String realName;
    @PhiField
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private String avatar;
    private String npi;
    @PhiField
    private String stateLicenseNumber;
    private String licenseState;
    @PhiField
    private String deaNumberLast4;
    private String taxonomyCode;
    private String credentials;
    private String specialty;
    private List<String> roles;
    private LocalDateTime createTime;
    private Integer failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginTime;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public static SysUserVO fromEntity(SysUser user, List<String> roles) {
        return new SysUserVO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getEmail(),
                user.getGender(),
                user.getStatus(),
                user.getAvatar(),
                user.getNpi(),
                user.getStateLicenseNumber(),
                user.getLicenseState(),
                maskLast4(user.getDeaNumber()),
                user.getTaxonomyCode(),
                user.getCredentials(),
                user.getSpecialty(),
                roles,
                user.getCreateTime(),
                user.getFailedAttempts(),
                user.getLockedUntil(),
                user.getLastLoginTime());
    }

    private static String maskLast4(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
