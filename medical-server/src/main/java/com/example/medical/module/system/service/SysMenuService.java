package com.example.medical.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysMenuFormDTO;
import com.example.medical.module.system.dto.SysMenuVO;
import com.example.medical.module.system.entity.SysMenu;
import com.example.medical.module.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper sysMenuMapper;

    public List<SysMenuVO> getTree() {
        List<SysMenu> menus = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSort));
        return SysMenuVO.buildTree(menus);
    }

    public List<SysMenuVO> listAll() {
        return sysMenuMapper.selectList(
                        new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort))
                .stream().map(SysMenuVO::fromEntity).toList();
    }

    @Transactional
    public void create(SysMenuFormDTO dto) {
        sysMenuMapper.insert(dto.toEntity());
    }

    @Transactional
    public void update(Long id, SysMenuFormDTO dto) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Menu not found");
        }
        dto.applyTo(menu);
        sysMenuMapper.updateById(menu);
    }

    @Transactional
    public void delete(Long id) {
        if (sysMenuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Delete child menus first");
        }
        sysMenuMapper.deleteById(id);
    }
}
