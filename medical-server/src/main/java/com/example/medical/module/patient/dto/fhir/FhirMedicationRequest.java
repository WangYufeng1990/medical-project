package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirMedicationRequest {
    private final String resourceType = "MedicationRequest";
    private String id;
    private String status = "active";
    private String intent = "order";
    private FhirReference subject;
    private FhirReference requester;
    private FhirCodeableConcept medicationCodeableConcept;
    private List<FhirDosage> dosageInstruction;
    private List<FhirCodeableConcept> reasonCode;

    public static FhirMedicationRequest of(String itemId, String patientRef, String doctorRef,
                                            String drugName, String ndcCode, String rxnormCode,
                                            String dosageText, String diagnosis) {
        FhirMedicationRequest m = new FhirMedicationRequest();
        m.id = "medication-request-" + itemId;
        m.subject = new FhirReference("Patient/" + patientRef, null);
        m.requester = new FhirReference("Practitioner/" + doctorRef, null);
        m.medicationCodeableConcept = buildMedicationConcept(drugName, ndcCode, rxnormCode);
        if (dosageText != null && !dosageText.isBlank()) {
            m.dosageInstruction = List.of(new FhirDosage(dosageText));
        }
        if (diagnosis != null && !diagnosis.isBlank()) {
            m.reasonCode = List.of(FhirCodeableConcept.textOnly(diagnosis));
        }
        return m;
    }

    private static FhirCodeableConcept buildMedicationConcept(String drugName,
                                                               String ndcCode, String rxnormCode) {
        List<FhirCoding> codings = new ArrayList<>();
        if (ndcCode != null && !ndcCode.isBlank()) {
            codings.add(new FhirCoding("http://hl7.org/fhir/sid/ndc", ndcCode, drugName));
        }
        if (rxnormCode != null && !rxnormCode.isBlank()) {
            codings.add(new FhirCoding("http://www.nlm.nih.gov/research/umls/rxnorm",
                    rxnormCode, drugName));
        }
        if (codings.isEmpty()) {
            return FhirCodeableConcept.textOnly(drugName);
        }
        return new FhirCodeableConcept(codings, drugName);
    }
}
