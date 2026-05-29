package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FhirReference(String reference, String display) {
}
