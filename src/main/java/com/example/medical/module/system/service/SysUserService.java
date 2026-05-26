package com.example.medical.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysUserFormDTO;
import com.example.medical.module.system.dto.SysUserVO;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.mapper.SysUserMapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<SysUserVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> pageParam = new Page<>(page, size);
        IPage<SysUser> result = sysUserMapper.selectPage(pageParam, wrapper);

        return result.convert(user -> {
            List<String> roles = sysUserMapper.selectRoleCodesByUserId(user.getId());
            return SysUserVO.fromEntity(user, roles);
        });
    }

    public SysUserVO getById(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "User not found");
        }
        List<String> roles = sysUserMapper.selectRoleCodesByUserId(id);
        return SysUserVO.fromEntity(user, roles);
    }

    @Transactional
    public void create(SysUserFormDTO dto) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername())) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Username already exists");
        }
        SysUser user = dto.toEntity();
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        sysUserMapper.insert(user);
    }

    @Transactional
    public void update(Long id, SysUserFormDTO dto) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "User not found");
        }
        dto.applyTo(user);
        sysUserMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id) {
        sysUserMapper.deleteById(id);
    }
}
