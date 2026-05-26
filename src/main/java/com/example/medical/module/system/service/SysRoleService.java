package com.example.medical.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysRoleFormDTO;
import com.example.medical.module.system.dto.SysRoleVO;
import com.example.medical.module.system.entity.SysRole;
import com.example.medical.module.system.mapper.SysRoleMapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper sysRoleMapper;

    public IPage<SysRoleVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysRole::getRoleName, keyword)
                        .or()
                        .like(SysRole::getRoleCode, keyword))
                .orderByDesc(SysRole::getCreateTime);

        Page<SysRole> pageParam = new Page<>(page, size);
        return sysRoleMapper.selectPage(pageParam, wrapper).convert(SysRoleVO::fromEntity);
    }

    @Transactional
    public void create(SysRoleFormDTO dto) {
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, dto.getRoleCode())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Role code already exists");
        }
        sysRoleMapper.insert(dto.toEntity());
    }

    @Transactional
    public void update(Long id, SysRoleFormDTO dto) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Role not found");
        }
        if (!role.getRoleCode().equals(dto.getRoleCode())
                && sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode, dto.getRoleCode())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Role code already exists");
        }
        dto.applyTo(role);
        sysRoleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        sysRoleMapper.deleteById(id);
    }
}
