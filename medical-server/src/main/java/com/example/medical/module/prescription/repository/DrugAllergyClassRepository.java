package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.DrugAllergyClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugAllergyClassRepository extends JpaRepository<DrugAllergyClass, Long> {

    List<DrugAllergyClass> findByDrugRxnormCodeIn(List<String> rxnormCodes);
}
