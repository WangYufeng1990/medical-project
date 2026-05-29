package com.example.medical.module.patient.dto.fhir;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FhirBundleEntry {
    private Object resource;
}
