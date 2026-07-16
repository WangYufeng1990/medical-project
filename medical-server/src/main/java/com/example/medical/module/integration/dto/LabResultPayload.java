package com.example.medical.module.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LabResultPayload {

    @NotBlank
    private String sourceMessageId;

    @NotBlank
    private String patientMrn;

    private String orderCode;

    private LocalDateTime collectionDate;

    @NotEmpty
    private List<ResultItem> results;

    @Data
    public static class ResultItem {
        @NotBlank
        private String loincCode;
        private String display;
        private String value;
        private String unit;
        private String referenceRange;
        private String abnormalFlag;
    }
}
