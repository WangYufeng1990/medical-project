package com.example.medical.module.patient.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final DoctorPatientScope doctorPatientScope;

    @GetMapping(value = "/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> metadata() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setDate(new java.util.Date());
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        cs.setSoftware(new CapabilityStatement.CapabilityStatementSoftwareComponent()
                .setName("Medical Management System").setVersion("0.0.1"));
        cs.addFormat("json").addFormat("xml");
        cs.setPublisher("Medical Project");
        cs.addImplementationGuide("http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core");

        CapabilityStatement.CapabilityStatementRestComponent rest = cs.addRest();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);

        CapabilityStatement.CapabilityStatementRestResourceComponent pr = rest.addResource();
        pr.setType("Patient");
        pr.setProfile("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
        pr.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        pr.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);

        CapabilityStatement.CapabilityStatementRestResourceComponent or = rest.addResource();
        or.setType("Observation");
        or.setProfile("http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab");
        or.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
        or.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);

        return encode(cs);
    }

    @GetMapping("/Patient/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    @com.example.medical.common.audit.Auditable(module = "patient", action = "FHIR_VIEW", phiAccess = true)
    public ResponseEntity<String> getPatient(@PathVariable Long id) {
        enforceEmergencyScope(id);
        doctorPatientScope.requireAccess(id);
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        org.hl7.fhir.r4.model.Patient fp = buildFhirPatient(p);
        return encode(fp);
    }

    private void enforceEmergencyScope(Long requestedPatientId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            if ("EMERGENCY".equals(user.getScope())) {
                if (user.getEmergencyPatientId() == null
                        || !user.getEmergencyPatientId().equals(requestedPatientId)) {
                    throw new BusinessException(ResultCode.FORBIDDEN,
                            "Emergency access restricted to patient " + user.getEmergencyPatientId());
                }
            }
        }
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
                doctorPatientScope.requireAccess(id);
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
            var scope = doctorPatientScope.resolve();
            org.springframework.data.domain.Page<Patient> page = scope == null
                    ? patientRepository.findAll(pageable)
                    : patientRepository.findByIdIn(scope, pageable);
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
