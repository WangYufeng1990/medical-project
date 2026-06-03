package com.example.medical.module.patient.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
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
public class FhirPatientController {

    private static final String SSN_SYSTEM = "http://hl7.org/fhir/sid/us-ssn";
    private static final String MRN_SYSTEM = "http://hl7.org/fhir/sid/us-mrn";

    private final PatientRepository patientRepository;
    private final FhirContext fhirContext;

    @GetMapping("/Patient/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<String> getPatient(@PathVariable Long id) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        org.hl7.fhir.r4.model.Patient fp = buildFhirPatient(p);
        return encode(fp);
    }

    @GetMapping("/Patient")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<String> searchPatients(
            @RequestParam(value = "_id", required = false) String idParam,
            @RequestParam(value = "_count", defaultValue = "50") int count,
            @RequestParam(value = "_offset", defaultValue = "0") int offset) {
        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTimestamp(new java.util.Date());

        int maxCount = Math.min(count, 500);

        if (idParam != null && !idParam.isBlank()) {
            try {
                Long id = Long.valueOf(idParam);
                patientRepository.findById(id).ifPresent(p ->
                        bundle.addEntry().setResource(buildFhirPatient(p))
                                .getRequest().setMethod(Bundle.HTTPVerb.GET)
                                .setUrl("Patient/" + id));
            } catch (NumberFormatException ignored) {
            }
        } else {
            org.springframework.data.domain.PageRequest pageable =
                    org.springframework.data.domain.PageRequest.of(
                            offset / maxCount, maxCount,
                            org.springframework.data.domain.Sort.by("id"));
            org.springframework.data.domain.Page<Patient> page =
                    patientRepository.findAll(pageable);
            for (Patient p : page.getContent()) {
                bundle.addEntry().setResource(buildFhirPatient(p))
                        .getRequest().setMethod(Bundle.HTTPVerb.GET)
                        .setUrl("Patient/" + p.getId());
            }
            bundle.setTotal((int) page.getTotalElements());
        }
        bundle.setTotal(bundle.getEntry().size());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(parser.encodeResourceToString(bundle));
    }

    private org.hl7.fhir.r4.model.Patient buildFhirPatient(Patient p) {
        org.hl7.fhir.r4.model.Patient fp = new org.hl7.fhir.r4.model.Patient();
        fp.setId(p.getId().toString());

        if (p.getMrn() != null) {
            fp.addIdentifier().setSystem(MRN_SYSTEM).setValue(p.getMrn());
        }
        if (p.getSsn() != null) {
            String digits = p.getSsn().replaceAll("[^0-9]", "");
            String masked = digits.length() > 4 ? "***-**-" + digits.substring(digits.length() - 4) : "***-**-" + digits;
            fp.addIdentifier().setSystem(SSN_SYSTEM).setValue(masked);
        }

        fp.addName().setUse(HumanName.NameUse.OFFICIAL).setFamily(p.getName());

        if (p.getDateOfBirth() != null) {
            fp.setBirthDate(java.sql.Date.valueOf(p.getDateOfBirth()));
        }
        if (p.getSexAtBirth() != null) {
            fp.setGender(switch (p.getSexAtBirth()) {
                case "M" -> Enumerations.AdministrativeGender.MALE;
                case "F" -> Enumerations.AdministrativeGender.FEMALE;
                default -> Enumerations.AdministrativeGender.UNKNOWN;
            });
        }
        if (p.getAddressLine1() != null) {
            Address addr = new Address();
            addr.setUse(Address.AddressUse.HOME);
            addr.setType(Address.AddressType.PHYSICAL);
            addr.addLine(p.getAddressLine1());
            if (p.getAddressLine2() != null && !p.getAddressLine2().isBlank()) addr.addLine(p.getAddressLine2());
            addr.setCity(p.getCity());
            addr.setState(p.getState());
            addr.setPostalCode(p.getZipCode());
            addr.setCountry("US");
            fp.addAddress(addr);
        }
        if (p.getPhoneMobile() != null) {
            fp.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(p.getPhoneMobile()).setUse(ContactPoint.ContactPointUse.MOBILE);
        }
        if (p.getEmail() != null) {
            fp.addTelecom().setSystem(ContactPoint.ContactPointSystem.EMAIL).setValue(p.getEmail());
        }
        return fp;
    }

    private ResponseEntity<String> encode(Resource resource) {
        IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(parser.encodeResourceToString(resource));
    }
}
