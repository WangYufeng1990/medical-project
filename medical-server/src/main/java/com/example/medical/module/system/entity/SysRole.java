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
@Table(name = "sys_role")
@SQLDelete(sql = "UPDATE sys_role SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class SysRole extends BaseEntity {

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "role_code")
    private String roleCode;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private Integer status;
}
