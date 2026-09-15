package com.example.medical.module.system.dto;

import com.example.medical.common.validation.ValidPassword;
import com.example.medical.module.system.entity.SysUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Staff account create payload.
 * <p>
 * The {@code @Size} limits follow from the AES storage rule: the column holds hex ciphertext of {@code 1 version byte + 12 IV + plaintext + 16 GCM tag}, so a {@code VARCHAR(n)} column fits at most {@code n/2 - 29} characters of plaintext. Without the bound, an over-long value reaches the database and surfaces as a 500 instead of a 400.
 */
@Data
public class SysUserFormDTO {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;

    private String realName;
    @Size(max = 71, message = "Phone must be at most 71 characters")
    private String phone;
    @Size(max = 121, message = "Email must be at most 121 characters")
    private String email;
    private Integer gender;
    private Integer status;
    private String npi;
    @Size(max = 71, message = "State license number must be at most 71 characters")
    private String stateLicenseNumber;
    private String licenseState;
    @Size(max = 71, message = "DEA number must be at most 71 characters")
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
