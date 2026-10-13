package com.example.medical.module.patient.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One inbound lab message, as the patient module receives it (52.5). The
 * integration module maps its own payload onto this.
 */
public record LabObservationCommand(String sourceMessageId, LocalDateTime collectionDate, List<Item> results) {

    public record Item(String loincCode, String display, String value, String unit,
                       String referenceRange, String abnormalFlag) {
    }
}
