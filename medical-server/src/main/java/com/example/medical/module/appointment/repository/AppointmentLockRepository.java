package com.example.medical.module.appointment.repository;

import com.example.medical.module.appointment.entity.AppointmentLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppointmentLockRepository extends JpaRepository<AppointmentLock, Long> {

    /** Ensure a lock row exists for the doctor (idempotent). */
    @Modifying
    @Query(value = "INSERT INTO appointment_lock (doctor_id, create_time, update_time, is_deleted, version) "
            + "VALUES (:doctorId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0) "
            + "ON DUPLICATE KEY UPDATE doctor_id = doctor_id", nativeQuery = true)
    void upsertDoctor(@Param("doctorId") Long doctorId);

    /** SELECT ... FOR UPDATE on the doctor's lock row — held until commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT al FROM AppointmentLock al WHERE al.doctorId = :doctorId")
    Optional<AppointmentLock> lockDoctor(@Param("doctorId") Long doctorId);
}
