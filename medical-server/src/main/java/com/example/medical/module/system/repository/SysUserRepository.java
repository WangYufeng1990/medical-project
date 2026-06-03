package com.example.medical.module.system.repository;

import com.example.medical.module.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE SysUser u SET u.failedAttempts = COALESCE(u.failedAttempts, 0) + 1, " +
            "u.lockedUntil = CASE WHEN COALESCE(u.failedAttempts, 0) + 1 >= 5 " +
            "THEN :lockedUntil ELSE NULL END WHERE u.id = :id")
    int incrementFailedAttempts(@Param("id") Long id,
                                @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

    @Modifying
    @Query("UPDATE SysUser u SET u.failedAttempts = 0, u.lockedUntil = NULL WHERE u.id = :id")
    void resetFailedAttempts(@Param("id") Long id);

    @Query(value = "SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = :userId AND r.status = 1", nativeQuery = true)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT DISTINCT m.permission FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = :userId AND m.status = 1 AND m.permission IS NOT NULL", nativeQuery = true)
    List<String> findPermissionsByUserId(@Param("userId") Long userId);
}
