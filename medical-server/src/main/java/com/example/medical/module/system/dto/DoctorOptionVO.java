package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.SysUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Minimal doctor identity for the appointment / prescription pickers. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DoctorOptionVO {

    private Long id;
    private String username;
    private String realName;

    public static DoctorOptionVO fromEntity(SysUser u) {
        String name = u.getRealName() != null ? u.getRealName() : u.getUsername();
        return new DoctorOptionVO(u.getId(), u.getUsername(), name);
    }
}
