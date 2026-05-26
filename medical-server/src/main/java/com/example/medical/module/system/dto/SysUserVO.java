package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SysUserVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private String avatar;
    private List<String> roles;
    private LocalDateTime createTime;

    public static SysUserVO fromEntity(SysUser user, List<String> roles) {
        return new SysUserVO(user.getId(), user.getUsername(), user.getRealName(),
                user.getPhone(), user.getEmail(), user.getGender(), user.getStatus(),
                user.getAvatar(), roles, user.getCreateTime());
    }
}
