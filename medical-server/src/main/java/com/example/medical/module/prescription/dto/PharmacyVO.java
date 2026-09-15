package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.PharmacyDirectory;
import lombok.Data;

@Data
public class PharmacyVO {

    private Long id;
    private String npi;
    private String name;
    private String addressLine1;
    private String city;
    private String state;
    private String zipCode;
    private String phone;

    /** The column stores 0/1; the API says boolean. */
    private Boolean supportsEpcs;

    public static PharmacyVO fromEntity(PharmacyDirectory p) {
        PharmacyVO vo = new PharmacyVO();
        vo.setId(p.getId());
        vo.setNpi(p.getNpi());
        vo.setName(p.getName());
        vo.setAddressLine1(p.getAddressLine1());
        vo.setCity(p.getCity());
        vo.setState(p.getState());
        vo.setZipCode(p.getZipCode());
        vo.setPhone(p.getPhone());
        vo.setSupportsEpcs(p.getSupportsEpcs() != null && p.getSupportsEpcs() == 1);
        return vo;
    }
}
