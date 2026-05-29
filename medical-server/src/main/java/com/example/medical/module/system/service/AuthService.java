package com.example.medical.module.system.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.LoginRequest;
import com.example.medical.module.system.dto.LoginResponse;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import com.example.medical.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedissonClient redissonClient;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }

        List<String> roleCodes = sysUserRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = sysUserRepository.findPermissionsByUserId(user.getId());

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleCodes);
        claims.put("permissions", permissions);

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), claims);
        String refreshToken = generateRefreshToken(user.getId());

        return LoginResponse.fromEntity(user, roleCodes, permissions, token, refreshToken);
    }

    public LoginResponse refresh(String oldRefreshToken) {
        RBucket<Long> bucket = redissonClient.getBucket("refresh:" + oldRefreshToken);
        Long userId = bucket.get();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Refresh token expired or already used");
        }

        bucket.delete();

        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "User not found"));
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Account is disabled");
        }

        List<String> roleCodes = sysUserRepository.findRoleCodesByUserId(userId);
        List<String> permissions = sysUserRepository.findPermissionsByUserId(userId);

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleCodes);
        claims.put("permissions", permissions);

        String token = jwtUtils.generateToken(userId, user.getUsername(), claims);
        String newRefreshToken = generateRefreshToken(userId);

        return LoginResponse.forRefresh(token, newRefreshToken, userId, user.getUsername(),
                roleCodes, permissions);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null) {
            redissonClient.getBucket("refresh:" + refreshToken).delete();
        }
    }

    private String generateRefreshToken(Long userId) {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RBucket<Long> bucket = redissonClient.getBucket("refresh:" + token);
        bucket.set(userId, Duration.ofMillis(refreshExpiration));

        return token;
    }
}
