package com.example.medical.module.system.dto;

import com.example.medical.common.validation.ValidPassword;
import com.example.medical.module.system.entity.SysUser;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysUserFormDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;

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

    public SysUser toEntity() {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(password);
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
        return user;
    }

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
