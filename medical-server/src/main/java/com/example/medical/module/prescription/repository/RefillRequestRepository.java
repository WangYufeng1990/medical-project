package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.RefillRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface RefillRequestRepository extends JpaRepository<RefillRequest, Long>, JpaSpecificationExecutor<RefillRequest> {
    List<RefillRequest> findByPatientIdOrderByRequestedAtDesc(Long patientId);
    List<RefillRequest> findByStatusOrderByRequestedAtDesc(String status);
    List<RefillRequest> findByStatusAndPatientIdInOrderByRequestedAtDesc(String status, Collection<Long> patientIds);
    boolean existsByPrescriptionIdAndStatus(Long prescriptionId, String status);
}
