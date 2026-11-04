package com.example.medical.module.appointment.repository;

import com.example.medical.module.appointment.entity.Appointment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /** Bounded future appointments for one patient (Round 58.1); no clinical text is selected. */
    @Query("SELECT a FROM Appointment a WHERE a.patientId = :patientId "
            + "AND a.appointmentTime >= :from AND a.appointmentTime <= :until "
            + "AND a.status NOT IN (:cancelled, :completed, :noShow) "
            + "AND a.isDeleted = 0 ORDER BY a.appointmentTime ASC")
    List<Appointment> findUpcomingForPatient(@Param("patientId") Long patientId,
                                             @Param("from") LocalDateTime from,
                                             @Param("until") LocalDateTime until,
                                             @Param("cancelled") int cancelled,
                                             @Param("completed") int completed,
                                             @Param("noShow") int noShow,
                                             Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.doctorId = :doctorId AND a.status <> :cancelledStatus " +
            "AND a.appointmentTime BETWEEN :start AND :end")
    List<Appointment> findConflicting(@Param("doctorId") Long doctorId,
                                       @Param("cancelledStatus") int cancelledStatus,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query("SELECT DISTINCT a.patientId FROM Appointment a WHERE a.doctorId = :doctorId")
    List<Long> findDistinctPatientIdsByDoctor(@Param("doctorId") Long doctorId);
}
