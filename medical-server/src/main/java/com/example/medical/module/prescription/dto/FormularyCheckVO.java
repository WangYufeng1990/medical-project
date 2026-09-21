package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.FormularyEntry;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Formulary lookup for one drug + payer. A miss carries only {@code found} and a
 * message, a hit carries the tier and the requirement flags; the unused keys are
 * omitted rather than sent as null.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FormularyCheckVO {

    private boolean found;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String drugName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tier;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean priorAuthRequired;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean stepTherapyRequired;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String alternatives;

    public static FormularyCheckVO notFound() {
        return new FormularyCheckVO(false, "Not in formulary or unknown insurance",
                null, null, null, null, null);
    }

    public static FormularyCheckVO fromEntity(FormularyEntry e) {
        return new FormularyCheckVO(true, null, e.getDrugName(), e.getTier(),
                e.getPriorAuthRequired() != null && e.getPriorAuthRequired(),
                e.getStepTherapyRequired() != null && e.getStepTherapyRequired(),
                e.getAlternatives() != null ? e.getAlternatives() : "");
    }
}
