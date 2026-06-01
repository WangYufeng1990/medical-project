package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.PharmacyDirectory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PharmacyDirectoryRepository extends JpaRepository<PharmacyDirectory, Long> {

    List<PharmacyDirectory> findByZipCodeStartingWith(String zipPrefix);

    List<PharmacyDirectory> findByStateOrderByNameAsc(String state);
}
