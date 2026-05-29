package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirCondition {
    private final String resourceType = "Condition";
    private String id;
    private FhirReference subject;
    private FhirCodeableConcept code;
    private FhirCodeableConcept clinicalStatus;

    public static FhirCondition of(String id, String patientRef, String conditionText) {
        FhirCondition c = new FhirCondition();
        c.id = "condition-" + id;
        c.subject = new FhirReference("Patient/" + patientRef, null);
        c.code = FhirCodeableConcept.textOnly(conditionText);
        c.clinicalStatus = new FhirCodeableConcept(
                java.util.List.of(new FhirCoding(
                        "http://terminology.hl7.org/CodeSystem/condition-clinical",
                        "active", "Active")),
                null);
        return c;
    }
}
