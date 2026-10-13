package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    Page<Patient> findByIdIn(Collection<Long> ids, Pageable pageable);

    /**
     * Single-column reads behind {@link com.example.medical.common.lookup.PatientLookup}.
     * Name and allergies are both encrypted, so loading the entity to reach one of
     * them would decrypt every PHI field on that row — once per row of every
     * appointment, bill, prescription and conversation list.
     */
    @Query("SELECT p.name FROM Patient p WHERE p.id = :id")
    Optional<String> findNameById(@Param("id") Long id);

    @Query("SELECT p.allergies FROM Patient p WHERE p.id = :id")
    Optional<String> findAllergiesById(@Param("id") Long id);

    /** Counts, so an existence check never decrypts a row it does not need. */
    @Query("SELECT COUNT(p) FROM Patient p WHERE p.id = :id")
    long countById(@Param("id") Long id);

    /** MRN is unique ({@code uk_mrn}), so at most one row answers. */
    @Query("SELECT p.id FROM Patient p WHERE p.mrn = :mrn")
    Optional<Long> findIdByMrn(@Param("mrn") String mrn);
}
