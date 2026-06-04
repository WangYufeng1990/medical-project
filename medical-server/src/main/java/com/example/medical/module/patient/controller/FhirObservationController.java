package com.example.medical.module.patient.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fhir")
@RequiredArgsConstructor
public class FhirObservationController {

    private final ObservationRepository observationRepository;
    private final FhirContext fhirContext;

    @GetMapping("/Observation/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<String> getObservation(@PathVariable Long id) {
        Observation o = observationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Observation not found"));
        return encode(buildFhirObservation(o));
    }

    @GetMapping("/Observation")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<String> searchObservations(
            @RequestParam(value = "patient", required = false) Long patientId,
            @RequestParam(value = "code", required = false) String loincCode,
            @RequestParam(value = "_count", defaultValue = "100") int count) {
        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTimestamp(new java.util.Date());

        int maxCount = Math.min(count, 500);

        List<Observation> observations;
        if (patientId != null && loincCode != null) {
            observations = observationRepository
                    .findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(patientId, loincCode);
        } else if (patientId != null) {
            observations = observationRepository.findByPatientIdOrderByEffectiveDateDesc(patientId);
        } else {
            observations = observationRepository.findAll(
                    org.springframework.data.domain.PageRequest.of(0, maxCount)).getContent();
        }

        for (Observation o : observations) {
            bundle.addEntry().setResource(buildFhirObservation(o))
                    .getRequest().setMethod(Bundle.HTTPVerb.GET)
                    .setUrl("Observation/" + o.getId());
        }
        bundle.setTotal(bundle.getEntry().size());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(parser.encodeResourceToString(bundle));
    }

    private org.hl7.fhir.r4.model.Observation buildFhirObservation(Observation o) {
        org.hl7.fhir.r4.model.Observation fhirObs =
                new org.hl7.fhir.r4.model.Observation();
        fhirObs.setId(o.getId().toString());
        fhirObs.setStatus(org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL);
        fhirObs.getSubject().setReference("Patient/" + o.getPatientId());

        fhirObs.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(o.getLoincCode())
                .setDisplay(o.getLoincDisplay());

        if (o.getObsValue() != null) {
            try {
                fhirObs.setValue(new Quantity()
                        .setValue(new java.math.BigDecimal(o.getObsValue()))
                        .setUnit(o.getUnit()));
            } catch (NumberFormatException e) {
                fhirObs.setValue(new StringType(o.getObsValue()));
            }
        }

        if ("H".equals(o.getAbnormalFlag()) || "HH".equals(o.getAbnormalFlag())) {
            fhirObs.addInterpretation().addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                    .setCode("H").setDisplay("High");
        } else if ("L".equals(o.getAbnormalFlag()) || "LL".equals(o.getAbnormalFlag())) {
            fhirObs.addInterpretation().addCoding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                    .setCode("L").setDisplay("Low");
        }

        if (o.getReferenceRange() != null) {
            fhirObs.addReferenceRange().setText(o.getReferenceRange());
        }

        if (o.getEffectiveDate() != null) {
            fhirObs.setEffective(new DateTimeType(
                    o.getEffectiveDate().toString() + ":00"));
        }

        return fhirObs;
    }

    private ResponseEntity<String> encode(Resource resource) {
        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(parser.encodeResourceToString(resource));
    }
}
