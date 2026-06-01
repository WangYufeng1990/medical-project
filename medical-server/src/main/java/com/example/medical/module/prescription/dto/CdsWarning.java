package com.example.medical.module.prescription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CdsWarning {
    private String type;
    private String severity;
    private String drugsInvolved;
    private String description;
    private String recommendation;
}
