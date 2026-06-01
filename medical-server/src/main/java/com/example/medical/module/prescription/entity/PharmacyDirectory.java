package com.example.medical.module.prescription.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "pharmacy_directory")
public class PharmacyDirectory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "npi", length = 10, unique = true)
    private String npi;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "address_line1", length = 100)
    private String addressLine1;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "phone", length = 200)
    private String phone;

    @Column(name = "supports_epcs")
    private Integer supportsEpcs;
}
