package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

    @Query("SELECT d FROM DrugInteraction d WHERE " +
            "(d.drugARxnorm = :codeA AND d.drugBRxnorm = :codeB) OR " +
            "(d.drugARxnorm = :codeB AND d.drugBRxnorm = :codeA)")
    List<DrugInteraction> findInteraction(@Param("codeA") String codeA, @Param("codeB") String codeB);
}
