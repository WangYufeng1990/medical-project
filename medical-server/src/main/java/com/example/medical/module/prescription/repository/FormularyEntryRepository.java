package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.FormularyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormularyEntryRepository extends JpaRepository<FormularyEntry, Long> {
    Optional<FormularyEntry> findByRxnormCodeAndInsurancePayer(String rxnormCode, String insurancePayer);
    List<FormularyEntry> findByRxnormCode(String rxnormCode);
}
