package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FhirCodeableConcept(List<FhirCoding> coding, String text) {

    public static FhirCodeableConcept textOnly(String text) {
        return new FhirCodeableConcept(null, text);
    }
}
