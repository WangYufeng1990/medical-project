package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysMenu;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Data
public class SysMenuVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String path;
    private String component;
    private String icon;
    private String type;
    private String permission;
    private Integer sort;
    private Integer status;
    private List<SysMenuVO> children;

    public static SysMenuVO fromEntity(SysMenu menu) {
        SysMenuVO vo = new SysMenuVO();
        vo.setId(menu.getId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setIcon(menu.getIcon());
        vo.setType(menu.getType());
        vo.setPermission(menu.getPermission());
        vo.setSort(menu.getSort());
        vo.setStatus(menu.getStatus());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    public static List<SysMenuVO> buildTree(List<SysMenu> menus) {
        List<SysMenuVO> all = menus.stream()
                .map(SysMenuVO::fromEntity)
                .sorted(Comparator.comparingInt(v -> v.getSort() != null ? v.getSort() : 0))
                .toList();

        List<SysMenuVO> roots = new ArrayList<>();
        for (SysMenuVO vo : all) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                all.stream()
                        .filter(p -> Objects.equals(p.getId(), vo.getParentId()))
                        .findFirst()
                        .ifPresent(p -> p.getChildren().add(vo));
            }
        }
        return roots;
    }
}
