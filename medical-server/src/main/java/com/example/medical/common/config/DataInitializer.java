package com.example.medical.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.medical.module.system.entity.SysMenu;
import com.example.medical.module.system.entity.SysRole;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.mapper.SysMenuMapper;
import com.example.medical.module.system.mapper.SysRoleMapper;
import com.example.medical.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }

        log.info("Initializing seed data...");

        long userId = 1;
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status) VALUES (?,?,?,?,?,?,?,?)",
                userId, "admin", passwordEncoder.encode("admin123"), "Administrator", "13800000000", "admin@medical.com", 1, 1);
        long doctorId = 2;
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, real_name, phone, email, gender, status) VALUES (?,?,?,?,?,?,?,?)",
                doctorId, "doctor1", passwordEncoder.encode("doctor123"), "Dr. Zhang Wei", "13800000001", "zhangwei@medical.com", 1, 1);

        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (?,?,?,?,?)",
                1L, "Admin", "ADMIN", "System administrator", 1);
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (?,?,?,?,?)",
                2L, "Doctor", "DOCTOR", "Doctor", 1);

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", 1L, 1L);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", 2L, 2L);

        Object[][] menus = {
                {1L, 0L, "Dashboard", "/dashboard", "dashboard/index", "Odometer", "MENU", null, 1, 1},
                {2L, 0L, "System", "/system", null, "Setting", "DIRECTORY", null, 10, 1},
                {3L, 2L, "Users", "/system/users", "system/users/index", "User", "MENU", "system:user:list", 11, 1},
                {4L, 2L, "Roles", "/system/roles", "system/roles/index", "Avatar", "MENU", "system:role:list", 12, 1},
                {5L, 2L, "Menus", "/system/menus", "system/menus/index", "Menu", "MENU", "system:menu:list", 13, 1},
                {10L, 0L, "Patients", "/patients", "patients/index", "UserFilled", "MENU", "patient:list", 20, 1},
                {11L, 0L, "Appointments", "/appointments", "appointments/index", "Calendar", "MENU", "appointment:list", 30, 1},
                {12L, 0L, "Prescriptions", "/prescriptions", "prescriptions/index", "Document", "MENU", "prescription:list", 40, 1},
                {13L, 0L, "Billing", "/billing", "billing/index", "Money", "MENU", "billing:list", 50, 1},
        };
        for (Object[] m : menus) {
            jdbcTemplate.update("INSERT INTO sys_menu (id, parent_id, menu_name, path, component, icon, type, permission, sort, status) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9]);
        }

        for (long mid : new long[]{1, 2, 3, 4, 5, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 1L, mid);
        }
        for (long mid : new long[]{1, 10, 11, 12, 13}) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?,?)", 2L, mid);
        }

        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, description, status) VALUES (?,?,?,?,?)",
                3L, "Patient", "PATIENT", "Patient", 1);

        jdbcTemplate.update("INSERT INTO patient (id, username, password, name, gender, age, phone, address) VALUES (?,?,?,?,?,?,?,?)",
                100L, "patient1", passwordEncoder.encode("patient123"), "Wang Xiao Ming", 1, 28,
                "13811112222", "Shanghai");

        log.info("Seed data initialized (admin/admin123, doctor1/doctor123, patient1/patient123)");
    }
}
