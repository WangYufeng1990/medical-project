package com.example.medical.module.system.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SQLDelete;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_menu")
@SQLDelete(sql = "UPDATE sys_menu SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class SysMenu extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "menu_name")
    private String menuName;

    @Column(name = "path")
    private String path;

    @Column(name = "component")
    private String component;

    @Column(name = "icon")
    private String icon;

    @Column(name = "type")
    private String type;

    @Column(name = "permission")
    private String permission;

    @Column(name = "sort")
    private Integer sort;

    @Column(name = "status")
    private Integer status;
}
