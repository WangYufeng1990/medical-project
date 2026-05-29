package com.example.medical.module.system.service;

import cn.hutool.core.util.StrUtil;
import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysUserFormDTO;
import com.example.medical.module.system.dto.SysUserVO;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<SysUserVO> page(long page, long size, String keyword) {
        Specification<SysUser> spec = (root, query, cb) -> {
            if (StrUtil.isBlank(keyword)) return null;
            String pattern = "%" + keyword + "%";
            return cb.or(
                    cb.like(root.get("username"), pattern),
                    cb.like(root.get("realName"), pattern));
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return sysUserRepository.findAll(spec, pageable).map(user -> {
            List<String> roles = sysUserRepository.findRoleCodesByUserId(user.getId());
            return SysUserVO.fromEntity(user, roles);
        });
    }

    @Cacheable(value = "users", key = "#id")
    public SysUserVO getById(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "User not found"));
        List<String> roles = sysUserRepository.findRoleCodesByUserId(id);
        return SysUserVO.fromEntity(user, roles);
    }

    @Transactional
    @Auditable(module = "system", action = "CREATE_USER")
    @CacheEvict(value = "users", allEntries = true)
    public void create(SysUserFormDTO dto) {
        if (sysUserRepository.existsByUsername(dto.getUsername())) {
            throw new BusinessException(ResultCode.CONFLICT, "Username already exists");
        }
        SysUser user = dto.toEntity();
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        sysUserRepository.save(user);
    }

    @Transactional
    @Auditable(module = "system", action = "UPDATE_USER")
    @CacheEvict(value = "users", key = "#id")
    public void update(Long id, SysUserFormDTO dto) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "User not found"));
        dto.applyTo(user);
        sysUserRepository.save(user);
    }

    @Transactional
    @Auditable(module = "system", action = "DELETE_USER")
    @CacheEvict(value = "users", key = "#id")
    public void delete(Long id) {
        sysUserRepository.deleteById(id);
    }
}
