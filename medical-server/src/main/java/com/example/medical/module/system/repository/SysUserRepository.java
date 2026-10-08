package com.example.medical.module.system.repository;

import com.example.medical.common.lookup.PrescriberIdentity;
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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SysUser u SET u.failedAttempts = COALESCE(u.failedAttempts, 0) + 1, " +
            "u.lockedUntil = CASE WHEN COALESCE(u.failedAttempts, 0) + 1 >= 5 " +
            "THEN :lockedUntil ELSE NULL END WHERE u.id = :id")
    int incrementFailedAttempts(@Param("id") Long id,
                                @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

    @Modifying(clearAutomatically = true)
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

    @Query("SELECT u.forceLogoutAfter FROM SysUser u WHERE u.id = :userId")
    java.time.LocalDateTime findForceLogoutAfterByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT DISTINCT u.* FROM sys_user u " +
            "INNER JOIN sys_user_role ur ON u.id = ur.user_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE r.role_code = 'DOCTOR' AND u.status = 1 AND u.is_deleted = 0 " +
            "ORDER BY u.real_name", nativeQuery = true)
    List<SysUser> findDoctors();

    /**
     * Reads behind {@link com.example.medical.common.lookup.StaffLookup}: a name,
     * and the pair a prescription is signed with. Loading the account to reach
     * them would carry the password hash and every other field with it; DEA is
     * encrypted, so the signing pair is fetched through the converter.
     */
    @Query("SELECT u.realName FROM SysUser u WHERE u.id = :id")
    Optional<String> findRealNameById(@Param("id") Long id);

    @Query("SELECT new com.example.medical.common.lookup.PrescriberIdentity(u.npi, u.deaNumber) "
            + "FROM SysUser u WHERE u.id = :id")
    Optional<PrescriberIdentity> findPrescriberIdentityById(@Param("id") Long id);
}
