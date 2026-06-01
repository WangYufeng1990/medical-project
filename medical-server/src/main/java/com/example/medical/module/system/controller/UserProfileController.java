package com.example.medical.module.system.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.common.validation.ValidPassword;
import com.example.medical.module.system.dto.SysUserVO;
import com.example.medical.module.system.entity.PasswordHistory;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.PasswordHistoryRepository;
import com.example.medical.module.system.repository.SysUserRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserRepository sysUserRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int PASSWORD_HISTORY_LIMIT = 3;
    private static final int PASSWORD_MAX_AGE_DAYS = 90;

    @GetMapping
    public Result<SysUserVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        SysUser user = sysUserRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "User not found"));
        List<String> roles = sysUserRepository.findRoleCodesByUserId(loginUser.getUserId());
        return Result.ok(SysUserVO.fromEntity(user, roles));
    }

    @PutMapping
    public Result<Void> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        SysUser user = sysUserRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "User not found"));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setGender(request.getGender());
        user.setNpi(request.getNpi());
        user.setLicenseState(request.getLicenseState());
        user.setTaxonomyCode(request.getTaxonomyCode());
        user.setCredentials(request.getCredentials());
        user.setSpecialty(request.getSpecialty());
        sysUserRepository.save(user);
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @Valid @RequestBody PasswordChangeRequest request) {
        SysUser user = sysUserRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Old password is incorrect");
        }

        String newPlainPassword = request.getNewPassword();
        if (isInPasswordHistory("SYS_USER", user.getId(), newPlainPassword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "New password must not match any of the last " + PASSWORD_HISTORY_LIMIT + " passwords");
        }

        PasswordHistory history = new PasswordHistory();
        history.setUserType("SYS_USER");
        history.setUserId(user.getId());
        history.setPasswordHash(user.getPassword());
        history.setChangedAt(user.getPasswordChangedAt());
        passwordHistoryRepository.save(history);

        user.setPassword(passwordEncoder.encode(newPlainPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        sysUserRepository.save(user);
        return Result.ok();
    }

    private boolean isInPasswordHistory(String userType, Long userId, String plainPassword) {
        List<PasswordHistory> recent = passwordHistoryRepository
                .findTop3ByUserTypeAndUserIdOrderByChangedAtDesc(userType, userId);
        return recent.stream().anyMatch(h -> passwordEncoder.matches(plainPassword, h.getPasswordHash()));
    }

    @Data
    static class ProfileUpdateRequest {
        private String realName;
        private String phone;
        private String email;
        private Integer gender;
        private String npi;
        private String licenseState;
        private String taxonomyCode;
        private String credentials;
        private String specialty;
    }

    @Data
    static class PasswordChangeRequest {
        @NotBlank(message = "Old password is required")
        private String oldPassword;
        @NotBlank(message = "New password is required")
        @ValidPassword
        private String newPassword;
    }
}
