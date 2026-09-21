package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.FormularyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormularyEntryRepository extends JpaRepository<FormularyEntry, Long> {

    List<FormularyEntry> findByRxnormCode(String rxnormCode);
}
