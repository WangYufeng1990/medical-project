package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.CarePlan;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CarePlanVO {

    private Long id;
    private Long patientId;
    private String title;
    private String goal;
    private String interventions;
    private LocalDate startDate;
    private LocalDate targetDate;
    private LocalDate completedDate;
    private String status;
    private Long createdBy;
    private String notes;
    private LocalDateTime createTime;

    public static CarePlanVO fromEntity(CarePlan cp) {
        return new CarePlanVO(
                cp.getId(), cp.getPatientId(), cp.getTitle(), cp.getGoal(),
                cp.getInterventions(), cp.getStartDate(), cp.getTargetDate(),
                cp.getCompletedDate(), cp.getStatus(), cp.getCreatedBy(),
                cp.getNotes(), cp.getCreateTime());
    }
}
