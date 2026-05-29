package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FhirExtension(String url, Object valueString, Object valueCoding) {

    public static FhirExtension stringExtension(String url, String value) {
        return new FhirExtension(url, value, null);
    }

    public static FhirExtension codingExtension(String url, String system, String code, String display) {
        return new FhirExtension(url, null, new FhirCoding(system, code, display));
    }
}
