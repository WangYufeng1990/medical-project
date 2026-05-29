package com.example.medical.module.patient.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.patient.dto.fhir.*;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientCaseService {

    private static final String SSN_OID = "urn:oid:2.16.840.1.113883.4.1";
    private static final String MRN_OID = "urn:oid:2.16.840.1.113883.4.2";
    private static final String RACE_EXT_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race";
    private static final String ETHNICITY_EXT_URL = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity";
    private static final String LANGUAGE_EXT_URL = "http://hl7.org/fhir/StructureDefinition/patient-interpreterRequired";
    private static final String LANGUAGE_CODE_SYSTEM = "urn:ietf:bcp:47";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;

    public FhirBundle getPatientCase(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        List<FhirBundleEntry> entries = new ArrayList<>();

        entries.add(new FhirBundleEntry(buildFhirPatient(patient)));

        if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isBlank()) {
            entries.add(new FhirBundleEntry(
                    FhirCondition.of(patientId.toString(), patientId.toString(), patient.getMedicalHistory())));
        }

        if (patient.getAllergies() != null && !patient.getAllergies().isBlank()) {
            entries.add(new FhirBundleEntry(
                    FhirAllergyIntolerance.of(patientId.toString(), patientId.toString(), patient.getAllergies())));
        }

        List<Appointment> appointments = appointmentRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "appointmentTime"));
        for (Appointment a : appointments) {
            entries.add(new FhirBundleEntry(buildFhirEncounter(a)));
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
                for (FhirMedicationRequest mr : buildFhirMedicationRequests(p, items)) {
                    entries.add(new FhirBundleEntry(mr));
                }
            }
        }

        return FhirBundle.of(entries);
    }

    private FhirPatient buildFhirPatient(Patient p) {
        FhirPatient fp = new FhirPatient();
        fp.setId(p.getId().toString());

        if (p.getMrn() != null) {
            fp.addIdentifier(new FhirIdentifier(MRN_OID, p.getMrn()));
        }
        if (p.getSsn() != null) {
            fp.addIdentifier(new FhirIdentifier(SSN_OID, p.getSsn()));
        }

        fp.addName(new FhirHumanName("official", p.getName()));

        if (p.getDateOfBirth() != null) {
            fp.setBirthDate(p.getDateOfBirth().toString());
        }

        fp.setGender(switch (p.getSexAtBirth() != null ? p.getSexAtBirth() : "") {
            case "M" -> "male";
            case "F" -> "female";
            default -> "unknown";
        });

        if (p.getAddressLine1() != null) {
            fp.addAddress(FhirAddress.usAddress("home",
                    p.getAddressLine1(), p.getAddressLine2(),
                    p.getCity(), p.getState(), p.getZipCode()));
        }

        if (p.getPhoneMobile() != null) {
            fp.addTelecom(new FhirContactPoint("phone", p.getPhoneMobile(), "mobile"));
        }
        if (p.getPhoneHome() != null) {
            fp.addTelecom(new FhirContactPoint("phone", p.getPhoneHome(), "home"));
        }
        if (p.getPhoneWork() != null) {
            fp.addTelecom(new FhirContactPoint("phone", p.getPhoneWork(), "work"));
        }
        if (p.getEmail() != null) {
            fp.addTelecom(new FhirContactPoint("email", p.getEmail(), null));
        }

        if (p.getRace() != null && !p.getRace().isBlank()) {
            fp.addExtension(FhirExtension.stringExtension(RACE_EXT_URL, p.getRace()));
        }
        if (p.getEthnicity() != null && !p.getEthnicity().isBlank()) {
            fp.addExtension(FhirExtension.stringExtension(ETHNICITY_EXT_URL, p.getEthnicity()));
        }
        if (p.getPreferredLanguage() != null && !p.getPreferredLanguage().isBlank()) {
            fp.addExtension(FhirExtension.codingExtension(LANGUAGE_EXT_URL,
                    LANGUAGE_CODE_SYSTEM, p.getPreferredLanguage(), null));
        }

        return fp;
    }

    private FhirEncounter buildFhirEncounter(Appointment a) {
        String status = switch (a.getStatus()) {
            case 0 -> "planned";
            case 1 -> "arrived";
            case 2 -> "cancelled";
            case 3 -> "finished";
            case 4 -> "cancelled";
            case 5 -> "cancelled";
            case 6 -> "in-progress";
            default -> "unknown";
        };
        return FhirEncounter.of(a.getId().toString(), a.getPatientId().toString(),
                status, a.getAppointmentTime() != null ? a.getAppointmentTime().toString() : null,
                a.getDescription());
    }

    private List<FhirMedicationRequest> buildFhirMedicationRequests(Prescription p, List<PrescriptionItem> items) {
        List<FhirMedicationRequest> requests = new ArrayList<>();
        for (PrescriptionItem item : items) {
            String dosageText = item.getSig() != null ? item.getSig()
                    : item.getDosage() + " " + item.getFrequency() + " x" + item.getDuration() + "d";
            requests.add(FhirMedicationRequest.of(
                    item.getId().toString(), p.getPatientId().toString(),
                    p.getDoctorId().toString(), item.getDrugName(),
                    item.getNdcCode(), item.getRxnormCode(),
                    dosageText, p.getDiagnosis()));
        }
        return requests;
    }
}
