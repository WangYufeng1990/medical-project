package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FhirAddress(
        String use,
        String type,
        String text,
        List<String> line,
        String city,
        String state,
        String postalCode,
        String country) {

    public FhirAddress(String text) {
        this(null, null, text, null, null, null, null, null);
    }

    public static FhirAddress usAddress(String use, String line1, String line2,
                                        String city, String state, String zip) {
        List<String> lines = (line2 != null && !line2.isBlank())
                ? List.of(line1, line2)
                : List.of(line1);
        return new FhirAddress(use, "physical", null, lines, city, state, zip, "US");
    }
}
