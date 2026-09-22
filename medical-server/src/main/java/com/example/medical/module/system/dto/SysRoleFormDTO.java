package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.EnabledStatus;
import com.example.medical.module.system.entity.SysRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysRoleFormDTO {

    @NotBlank(message = "Role name is required")
    private String roleName;

    @NotBlank(message = "Role code is required")
    private String roleCode;

    private String description;
    private Integer status;

    public SysRole toEntity() {
        SysRole role = new SysRole();
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setDescription(description);
        role.setStatus(EnabledStatus.parse(status).value());
        return role;
    }

    public void applyTo(SysRole role) {
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setDescription(description);
        // An update that omits the status keeps it: a partial payload must not
        // switch an account, role or menu back on.
        if (status != null) role.setStatus(EnabledStatus.parse(status).value());
    }
}
