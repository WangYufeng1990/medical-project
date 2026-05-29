package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FhirContactPoint(String system, String value, String use) {
    public FhirContactPoint(String system, String value) {
        this(system, value, null);
    }
}
