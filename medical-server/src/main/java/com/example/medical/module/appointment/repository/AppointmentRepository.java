package com.example.medical.module.appointment.repository;

import com.example.medical.module.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    @Query("SELECT a FROM Appointment a WHERE a.doctorId = :doctorId AND a.status <> 2 " +
            "AND a.appointmentTime BETWEEN :start AND :end")
    List<Appointment> findConflicting(@Param("doctorId") Long doctorId,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);
}
