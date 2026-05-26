package com.example.medical.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private Long parentId;
    private String menuName;
    private String path;
    private String component;
    private String icon;
    private String type;
    private String permission;
    private Integer sort;
    private Integer status;
}
