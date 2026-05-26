package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysMenu;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysMenuFormDTO {

    private Long parentId;

    @NotBlank(message = "Menu name is required")
    private String menuName;

    private String path;
    private String component;
    private String icon;

    @NotBlank(message = "Menu type is required")
    private String type;

    private String permission;
    private Integer sort;
    private Integer status;

    public SysMenu toEntity() {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId != null ? parentId : 0L);
        menu.setMenuName(menuName);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setType(type);
        menu.setPermission(permission);
        menu.setSort(sort != null ? sort : 0);
        menu.setStatus(status != null ? status : 1);
        return menu;
    }

    public void applyTo(SysMenu menu) {
        menu.setParentId(parentId != null ? parentId : 0L);
        menu.setMenuName(menuName);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setIcon(icon);
        menu.setType(type);
        menu.setPermission(permission);
        menu.setSort(sort);
        menu.setStatus(status);
    }
}
