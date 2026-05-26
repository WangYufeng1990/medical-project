package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysUser;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysUserFormDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    private String realName;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;

    public SysUser toEntity() {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);
        user.setStatus(status);
        return user;
    }

    public void applyTo(SysUser user) {
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);
        user.setStatus(status);
    }
}
