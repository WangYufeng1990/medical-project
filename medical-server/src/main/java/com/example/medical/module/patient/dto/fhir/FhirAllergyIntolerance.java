package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirAllergyIntolerance {
    private final String resourceType = "AllergyIntolerance";
    private String id;
    private FhirReference patient;
    private FhirCodeableConcept code;
    private FhirCodeableConcept clinicalStatus;

    public static FhirAllergyIntolerance of(String id, String patientRef, String allergyText) {
        FhirAllergyIntolerance a = new FhirAllergyIntolerance();
        a.id = "allergy-" + id;
        a.patient = new FhirReference("Patient/" + patientRef, null);
        a.code = FhirCodeableConcept.textOnly(allergyText);
        a.clinicalStatus = new FhirCodeableConcept(
                java.util.List.of(new FhirCoding(
                        "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                        "active", "Active")),
                null);
        return a;
    }
}
