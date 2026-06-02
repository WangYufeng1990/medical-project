package com.example.medical.module.system.service;

import cn.hutool.core.util.StrUtil;
import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysRoleFormDTO;
import com.example.medical.module.system.dto.SysRoleVO;
import com.example.medical.module.system.entity.SysRole;
import com.example.medical.module.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleRepository sysRoleRepository;

    public Page<SysRoleVO> page(long page, long size, String keyword) {
        Specification<SysRole> spec = (root, query, cb) -> {
            if (StrUtil.isBlank(keyword)) return null;
            String pattern = "%" + keyword + "%";
            return cb.or(
                    cb.like(root.get("roleName"), pattern),
                    cb.like(root.get("roleCode"), pattern));
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return sysRoleRepository.findAll(spec, pageable).map(SysRoleVO::fromEntity);
    }

    @Transactional
    @Auditable(module = "system", action = "CREATE_ROLE")
    public void create(SysRoleFormDTO dto) {
        if (sysRoleRepository.existsByRoleCode(dto.getRoleCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "Role code already exists");
        }
        sysRoleRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "system", action = "UPDATE_ROLE")
    public void update(Long id, SysRoleFormDTO dto) {
        SysRole role = sysRoleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Role not found"));
        if (!role.getRoleCode().equals(dto.getRoleCode())
                && sysRoleRepository.existsByRoleCode(dto.getRoleCode())) {
            throw new BusinessException(ResultCode.CONFLICT, "Role code already exists");
        }
        dto.applyTo(role);
        sysRoleRepository.save(role);
    }

    @Transactional
    @Auditable(module = "system", action = "DELETE_ROLE")
    public void delete(Long id) {
        sysRoleRepository.deleteById(id);
    }
}
