package com.example.medical.module.patient.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.example.medical.module.patient.service.PatientCaseService;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientCaseController {

    private final PatientCaseService patientCaseService;
    private final FhirContext fhirContext;

    @GetMapping("/{id}/case")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<String> getCase(@PathVariable Long id) {
        Bundle bundle = patientCaseService.getPatientCase(id);
        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(parser.encodeResourceToString(bundle));
    }
}
