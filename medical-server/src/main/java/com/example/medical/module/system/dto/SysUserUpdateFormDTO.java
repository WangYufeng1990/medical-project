package com.example.medical.module.system.dto;

import com.example.medical.common.validation.ValidPassword;
import com.example.medical.module.system.entity.SysUser;
import lombok.Data;

/**
 * Update payload for sys_user: password is OPTIONAL (a blank/absent value
 * keeps the current password; a non-blank value resets it, Review III H4).
 */
@Data
public class SysUserUpdateFormDTO {

    private String realName;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private String npi;
    private String stateLicenseNumber;
    private String licenseState;
    private String deaNumber;
    private String taxonomyCode;
    private String credentials;
    private String specialty;

    @ValidPassword
    private String password;

    public void applyTo(SysUser user) {
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setGender(gender);
        user.setStatus(status);
        user.setNpi(npi);
        user.setStateLicenseNumber(stateLicenseNumber);
        user.setLicenseState(licenseState);
        user.setDeaNumber(deaNumber);
        user.setTaxonomyCode(taxonomyCode);
        user.setCredentials(credentials);
        user.setSpecialty(specialty);
    }
}
