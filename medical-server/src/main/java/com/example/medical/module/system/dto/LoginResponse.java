package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
    private List<String> permissions;

    public static LoginResponse fromEntity(SysUser user, List<String> roles,
                                           List<String> permissions, String token) {
        return new LoginResponse(token, user.getId(), user.getUsername(),
                user.getRealName(), roles, permissions);
    }
}
