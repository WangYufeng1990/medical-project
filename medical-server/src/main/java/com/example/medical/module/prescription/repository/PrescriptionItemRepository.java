package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long>, JpaSpecificationExecutor<PrescriptionItem> {

    List<PrescriptionItem> findByPrescriptionId(Long prescriptionId);

    @Modifying
    @Query("DELETE FROM PrescriptionItem i WHERE i.prescriptionId = :prescriptionId")
    void deleteByPrescriptionId(@Param("prescriptionId") Long prescriptionId);
}
