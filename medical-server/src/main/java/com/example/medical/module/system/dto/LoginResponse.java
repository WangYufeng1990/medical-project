package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class LoginResponse {

    private String token;
    private String refreshToken;
    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
    private List<String> permissions;

    public static LoginResponse fromEntity(SysUser user, List<String> roles,
                                           List<String> permissions, String token,
                                           String refreshToken) {
        return new LoginResponse(token, refreshToken, user.getId(), user.getUsername(),
                user.getRealName(), roles, permissions);
    }

    public static LoginResponse forRefresh(String token, String refreshToken,
                                            Long userId, String username,
                                            List<String> roles, List<String> permissions) {
        return new LoginResponse(token, refreshToken, userId, username, null, roles, permissions);
    }
}
