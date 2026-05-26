package com.example.medical.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.LoginRequest;
import com.example.medical.module.system.dto.LoginResponse;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.mapper.SysUserMapper;
import com.example.medical.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }

        List<String> roleCodes = sysUserMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserMapper.selectPermissionsByUserId(user.getId());

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleCodes);
        claims.put("permissions", permissions);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), claims);

        return LoginResponse.fromEntity(user, roleCodes, permissions, token);
    }
}
