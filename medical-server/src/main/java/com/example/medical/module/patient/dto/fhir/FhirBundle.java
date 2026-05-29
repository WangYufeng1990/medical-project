package com.example.medical.module.patient.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Data
@JsonInclude(NON_NULL)
public class FhirBundle {
    private final String resourceType = "Bundle";
    private final String type = "searchset";
    private int total;
    private List<FhirBundleEntry> entry;

    public static FhirBundle of(List<FhirBundleEntry> entries) {
        FhirBundle bundle = new FhirBundle();
        bundle.total = entries.size();
        bundle.entry = entries;
        return bundle;
    }
}
