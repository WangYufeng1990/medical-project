package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysRoleVO {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer status;
    private LocalDateTime createTime;

    public static SysRoleVO fromEntity(SysRole role) {
        return new SysRoleVO(role.getId(), role.getRoleName(), role.getRoleCode(),
                role.getDescription(), role.getStatus(), role.getCreateTime());
    }
}
