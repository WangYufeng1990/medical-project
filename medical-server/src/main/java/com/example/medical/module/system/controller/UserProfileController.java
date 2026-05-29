package com.example.medical.module.system.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.system.dto.SysUserVO;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserRepository.save(user);
        return Result.ok();
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
        private String newPassword;
    }
}
