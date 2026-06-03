package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.PatientAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientAuthRepository extends JpaRepository<PatientAuth, Long> {

    Optional<PatientAuth> findByUsername(String username);

    Optional<PatientAuth> findByPatientId(Long patientId);

    @Modifying
    @Query("UPDATE PatientAuth a SET a.failedAttempts = COALESCE(a.failedAttempts, 0) + 1, " +
            "a.lockedUntil = CASE WHEN COALESCE(a.failedAttempts, 0) + 1 >= 5 " +
            "THEN :lockedUntil ELSE NULL END WHERE a.id = :id")
    int incrementFailedAttempts(@Param("id") Long id,
                                @Param("lockedUntil") java.time.LocalDateTime lockedUntil);

    @Modifying
    @Query("UPDATE PatientAuth a SET a.failedAttempts = 0, a.lockedUntil = NULL WHERE a.id = :id")
    void resetFailedAttempts(@Param("id") Long id);
}
