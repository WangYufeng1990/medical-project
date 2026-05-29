package com.example.medical.module.system.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.dto.SysMenuFormDTO;
import com.example.medical.module.system.dto.SysMenuVO;
import com.example.medical.module.system.entity.SysMenu;
import com.example.medical.module.system.repository.SysMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuRepository sysMenuRepository;

    public List<SysMenuVO> getTree() {
        List<SysMenu> menus = sysMenuRepository.findAllByOrderBySortAsc();
        return SysMenuVO.buildTree(menus);
    }

    public List<SysMenuVO> listAll() {
        return sysMenuRepository.findAllByOrderBySortAsc()
                .stream().map(SysMenuVO::fromEntity).toList();
    }

    @Transactional
    public void create(SysMenuFormDTO dto) {
        sysMenuRepository.save(dto.toEntity());
    }

    @Transactional
    public void update(Long id, SysMenuFormDTO dto) {
        SysMenu menu = sysMenuRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Menu not found"));
        dto.applyTo(menu);
        sysMenuRepository.save(menu);
    }

    @Transactional
    public void delete(Long id) {
        if (sysMenuRepository.countByParentId(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "Delete child menus first");
        }
        sysMenuRepository.deleteById(id);
    }
}
