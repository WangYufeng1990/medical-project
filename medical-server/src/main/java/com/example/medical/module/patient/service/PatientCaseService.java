package com.example.medical.module.patient.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientCaseService {

    private static final String SSN_SYSTEM = "http://hl7.org/fhir/sid/us-ssn";
    private static final String MRN_SYSTEM = "http://hl7.org/fhir/sid/us-mrn";
    private static final String NDC_SYSTEM = "http://hl7.org/fhir/sid/ndc";
    private static final String RXNORM_SYSTEM = "http://www.nlm.nih.gov/research/umls/rxnorm";
    private static final String RACE_EXT_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race";
    private static final String ETHNICITY_EXT_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity";
    private static final String LANGUAGE_EXT_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;

    public Bundle getPatientCase(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTimestamp(new Date());

        bundle.addEntry().setResource(buildFhirPatient(patient)).getRequest()
                .setMethod(Bundle.HTTPVerb.PUT)
                .setUrl("Patient/" + patient.getId());

        if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isBlank()) {
            bundle.addEntry().setResource(buildCondition(patient)).getRequest()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl("Condition/" + patient.getId() + "-condition");
        }

        if (patient.getAllergies() != null && !patient.getAllergies().isBlank()) {
            bundle.addEntry().setResource(buildAllergyIntolerance(patient)).getRequest()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl("AllergyIntolerance/" + patient.getId() + "-allergy");
        }

        List<Appointment> appointments = appointmentRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "appointmentTime"));
        for (Appointment a : appointments) {
            bundle.addEntry().setResource(buildEncounter(a)).getRequest()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl("Encounter/" + a.getId());
        }

        List<Prescription> prescriptions = prescriptionRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "prescriptionDate"));
        if (!prescriptions.isEmpty()) {
            List<Long> prescriptionIds = prescriptions.stream()
                    .map(Prescription::getId).toList();
            List<PrescriptionItem> allItems = prescriptionItemRepository
                    .findAll((root, query, cb) -> root.get("prescriptionId").in(prescriptionIds));
            Map<Long, List<PrescriptionItem>> itemsByPrescription = allItems.stream()
                    .collect(Collectors.groupingBy(PrescriptionItem::getPrescriptionId));

            for (Prescription p : prescriptions) {
                List<PrescriptionItem> items = itemsByPrescription.getOrDefault(p.getId(), List.of());
                for (MedicationRequest mr : buildMedicationRequests(p, items)) {
                    bundle.addEntry().setResource(mr).getRequest()
                            .setMethod(Bundle.HTTPVerb.PUT)
                            .setUrl("MedicationRequest/" + mr.getId());
                }
            }
        }

        bundle.setTotal(bundle.getEntry().size());
        return bundle;
    }

    private org.hl7.fhir.r4.model.Patient buildFhirPatient(Patient p) {
        org.hl7.fhir.r4.model.Patient fp = new org.hl7.fhir.r4.model.Patient();
        fp.setId(p.getId().toString());

        if (p.getMrn() != null) {
            fp.addIdentifier()
                    .setSystem(MRN_SYSTEM)
                    .setValue(p.getMrn());
        }
        if (p.getSsn() != null) {
            fp.addIdentifier()
                    .setSystem(SSN_SYSTEM)
                    .setValue(maskSsn(p.getSsn()));
        }

        fp.addName()
                .setUse(HumanName.NameUse.OFFICIAL)
                .setFamily(p.getName());

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
            if (p.getAddressLine2() != null && !p.getAddressLine2().isBlank()) {
                addr.addLine(p.getAddressLine2());
            }
            addr.setCity(p.getCity());
            addr.setState(p.getState());
            addr.setPostalCode(p.getZipCode());
            addr.setCountry("US");
            fp.addAddress(addr);
        }

        if (p.getPhoneMobile() != null) {
            fp.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(p.getPhoneMobile())
                    .setUse(ContactPoint.ContactPointUse.MOBILE);
        }
        if (p.getPhoneHome() != null) {
            fp.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(p.getPhoneHome())
                    .setUse(ContactPoint.ContactPointUse.HOME);
        }
        if (p.getPhoneWork() != null) {
            fp.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.PHONE)
                    .setValue(p.getPhoneWork())
                    .setUse(ContactPoint.ContactPointUse.WORK);
        }
        if (p.getEmail() != null) {
            fp.addTelecom()
                    .setSystem(ContactPoint.ContactPointSystem.EMAIL)
                    .setValue(p.getEmail());
        }

        if (p.getRace() != null && !p.getRace().isBlank()) {
            fp.addExtension()
                    .setUrl(RACE_EXT_URL)
                    .setValue(new StringType(p.getRace()));
        }
        if (p.getEthnicity() != null && !p.getEthnicity().isBlank()) {
            fp.addExtension()
                    .setUrl(ETHNICITY_EXT_URL)
                    .setValue(new StringType(p.getEthnicity()));
        }
        if (p.getPreferredLanguage() != null && !p.getPreferredLanguage().isBlank()) {
            fp.addExtension()
                    .setUrl(LANGUAGE_EXT_URL)
                    .setValue(new StringType(p.getPreferredLanguage()));
        }

        return fp;
    }

    private static String maskSsn(String ssn) {
        if (ssn == null || ssn.isBlank()) return ssn;
        String digits = ssn.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) return "***-**-" + digits;
        return "***-**-" + digits.substring(digits.length() - 4);
    }

    private Condition buildCondition(Patient p) {
        Condition condition = new Condition();
        condition.setId(p.getId() + "-condition");
        condition.getSubject().setReference("Patient/" + p.getId());
        condition.getCode().setText(p.getMedicalHistory());
        condition.setClinicalStatus(
                new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                        .setCode("active")
                        .setDisplay("Active")));
        return condition;
    }

    private AllergyIntolerance buildAllergyIntolerance(Patient p) {
        AllergyIntolerance allergy = new AllergyIntolerance();
        allergy.setId(p.getId() + "-allergy");
        allergy.getPatient().setReference("Patient/" + p.getId());
        allergy.getCode().setText(p.getAllergies());
        return allergy;
    }

    private Encounter buildEncounter(Appointment a) {
        Encounter encounter = new Encounter();
        encounter.setId(a.getId().toString());
        encounter.getSubject().setReference("Patient/" + a.getPatientId());
        if (a.getDoctorId() != null) {
            encounter.addParticipant()
                    .getIndividual().setReference("Practitioner/" + a.getDoctorId());
        }

        encounter.setStatus(switch (a.getStatus()) {
            case 0 -> Encounter.EncounterStatus.PLANNED;
            case 1 -> Encounter.EncounterStatus.ARRIVED;
            case 3 -> Encounter.EncounterStatus.FINISHED;
            case 6 -> Encounter.EncounterStatus.INPROGRESS;
            default -> Encounter.EncounterStatus.CANCELLED;
        });

        if (a.getAppointmentTime() != null) {
            encounter.getPeriod().setStart(java.sql.Timestamp.valueOf(a.getAppointmentTime()));
        }
        if (a.getDescription() != null) {
            encounter.addType().setText(a.getDescription());
        }

        return encounter;
    }

    private List<MedicationRequest> buildMedicationRequests(Prescription p, List<PrescriptionItem> items) {
        return items.stream().map(item -> {
            MedicationRequest mr = new MedicationRequest();
            mr.setId("medication-request-" + item.getId());
            mr.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
            mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
            mr.getSubject().setReference("Patient/" + p.getPatientId());
            mr.getRequester().setReference("Practitioner/" + p.getDoctorId());

            CodeableConcept med = mr.getMedicationCodeableConcept();
            med.setText(item.getDrugName());
            if (item.getNdcCode() != null && !item.getNdcCode().isBlank()) {
                med.addCoding().setSystem(NDC_SYSTEM).setCode(item.getNdcCode())
                        .setDisplay(item.getDrugName());
            }
            if (item.getRxnormCode() != null && !item.getRxnormCode().isBlank()) {
                med.addCoding().setSystem(RXNORM_SYSTEM).setCode(item.getRxnormCode())
                        .setDisplay(item.getDrugName());
            }

            String sig = item.getSig() != null ? item.getSig()
                    : item.getDosage() + " " + item.getFrequency()
                    + " x" + item.getDuration() + "d";
            mr.addDosageInstruction().setText(sig);

            if (p.getDiagnosis() != null && !p.getDiagnosis().isBlank()) {
                mr.addReasonCode().setText(p.getDiagnosis());
            }

            if (item.getDaysSupply() != null || item.getRefills() != null) {
                mr.getDispenseRequest().getValidityPeriod()
                        .setStart(new Date());
                if (item.getDaysSupply() != null) {
                    Duration supplyDuration = new Duration();
                    supplyDuration.setValue(item.getDaysSupply());
                    supplyDuration.setUnit("days");
                    supplyDuration.setSystem("http://unitsofmeasure.org");
                    supplyDuration.setCode("d");
                    mr.getDispenseRequest()
                            .setExpectedSupplyDuration(supplyDuration);
                }
                if (item.getRefills() != null) {
                    mr.getDispenseRequest().setNumberOfRepeatsAllowed(item.getRefills());
                }
            }

            return mr;
        }).toList();
    }
}
