package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirEncounter {
    private final String resourceType = "Encounter";
    private String id;
    private String status;
    private FhirReference subject;
    private FhirPeriod period;
    private List<FhirCodeableConcept> reasonCode;

    public static FhirEncounter of(String id, String patientRef, String status,
                                    String startTime, String reason) {
        FhirEncounter e = new FhirEncounter();
        e.id = "encounter-" + id;
        e.status = status;
        e.subject = new FhirReference("Patient/" + patientRef, null);
        e.period = new FhirPeriod(startTime);
        if (reason != null && !reason.isBlank()) {
            e.reasonCode = List.of(FhirCodeableConcept.textOnly(reason));
        }
        return e;
    }
}
