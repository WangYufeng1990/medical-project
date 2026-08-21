package com.example.medical.module.prescription.service;

import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.dto.CdsWarning;
import com.example.medical.module.prescription.entity.DrugAllergyClass;
import com.example.medical.module.prescription.entity.DrugInteraction;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.DrugAllergyClassRepository;
import com.example.medical.module.prescription.repository.DrugInteractionRepository;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdsService {

    private final DrugInteractionRepository drugInteractionRepository;
    private final DrugAllergyClassRepository drugAllergyClassRepository;
    private final PatientRepository patientRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;

    public List<CdsWarning> checkDrugInteractions(List<PrescriptionItem> items) {
        List<CdsWarning> warnings = new ArrayList<>();
        if (items == null || items.size() < 2) return warnings;

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                String codeA = items.get(i).getRxnormCode();
                String codeB = items.get(j).getRxnormCode();
                if (codeA == null || codeB == null) continue;

                List<DrugInteraction> interactions = drugInteractionRepository
                        .findInteraction(codeA, codeB);
                for (DrugInteraction di : interactions) {
                    warnings.add(new CdsWarning(
                            "DRUG_DRUG",
                            di.getSeverity(),
                            items.get(i).getDrugName() + " + " + items.get(j).getDrugName(),
                            di.getDescription(),
                            di.getRecommendation()));
                    log.info("CDS Drug-Drug Interaction: {} + {} = {} ({})",
                            items.get(i).getDrugName(), items.get(j).getDrugName(),
                            di.getSeverity(), di.getDescription());
                }
            }
        }
        return warnings;
    }

    /**
     * Interactions between the new items and the patient's other ACTIVE
     * prescriptions (Review III H3) — a new script that dangerously interacts
     * with ongoing therapy is no longer invisible.
     */
    public List<CdsWarning> checkActiveMedicationInteractions(Long patientId,
                                                              List<PrescriptionItem> newItems) {
        List<CdsWarning> warnings = new ArrayList<>();
        if (patientId == null || newItems == null || newItems.isEmpty()) return warnings;

        List<PrescriptionItem> existing = prescriptionRepository
                .findByPatientIdAndRxStatus(patientId, "active")
                .stream()
                .flatMap(rx -> prescriptionItemRepository.findByPrescriptionId(rx.getId()).stream())
                .toList();
        if (existing.isEmpty()) return warnings;

        for (PrescriptionItem existingItem : existing) {
            for (PrescriptionItem newItem : newItems) {
                String codeA = existingItem.getRxnormCode();
                String codeB = newItem.getRxnormCode();
                if (codeA == null || codeB == null || codeA.equals(codeB)) continue;
                for (DrugInteraction di : drugInteractionRepository.findInteraction(codeA, codeB)) {
                    warnings.add(new CdsWarning(
                            "DRUG_DRUG",
                            di.getSeverity(),
                            existingItem.getDrugName() + " + " + newItem.getDrugName() + " (active Rx)",
                            di.getDescription(),
                            di.getRecommendation()));
                }
            }
        }
        return warnings;
    }

    public List<CdsWarning> checkAllergyContraindications(Long patientId,
                                                           List<PrescriptionItem> items) {
        List<CdsWarning> warnings = new ArrayList<>();
        if (items == null || items.isEmpty()) return warnings;

        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null || patient.getAllergies() == null
                || patient.getAllergies().isBlank()) return warnings;

        String allergies = patient.getAllergies().toLowerCase();

        List<String> rxnormCodes = items.stream()
                .map(PrescriptionItem::getRxnormCode)
                .filter(c -> c != null && !c.isBlank())
                .toList();
        if (rxnormCodes.isEmpty()) return warnings;

        List<DrugAllergyClass> allergyClasses = drugAllergyClassRepository
                .findByDrugRxnormCodeIn(rxnormCodes);

        for (DrugAllergyClass dac : allergyClasses) {
            if (allergies.contains(dac.getAllergyClass().toLowerCase())) {
                for (PrescriptionItem item : items) {
                    if (dac.getDrugRxnormCode().equals(item.getRxnormCode())) {
                        warnings.add(new CdsWarning(
                                "DRUG_ALLERGY",
                                "contraindicated",
                                item.getDrugName() + " vs " + dac.getAllergyClass(),
                                "Patient has known allergy to " + dac.getAllergyClass()
                                        + ". " + item.getDrugName() + " is contraindicated.",
                                "Consider alternative medication. Document override reason if essential."));
                        log.warn("CDS Drug-Allergy Contraindication: {} for allergic patient {}",
                                item.getDrugName(), patientId);
                    }
                }
                // Cross-reactive drugs (Review III H4) — e.g. cephalosporin when
                // the patient is allergic to penicillin.
                Set<String> crossCodes = crossReactiveCodes(dac);
                if (!crossCodes.isEmpty()) {
                    for (PrescriptionItem item : items) {
                        if (item.getRxnormCode() != null
                                && crossCodes.contains(item.getRxnormCode())) {
                            warnings.add(new CdsWarning(
                                    "DRUG_ALLERGY",
                                    "contraindicated",
                                    item.getDrugName() + " vs " + dac.getAllergyClass() + " (cross-reactive)",
                                    "Patient has known allergy to " + dac.getAllergyClass()
                                            + "; " + item.getDrugName() + " is cross-reactive and contraindicated.",
                                    "Consider alternative medication. Document override reason if essential."));
                        }
                    }
                }
            }
        }
        return warnings;
    }

    private static Set<String> crossReactiveCodes(DrugAllergyClass dac) {
        if (dac.getCrossReactiveCodes() == null || dac.getCrossReactiveCodes().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(dac.getCrossReactiveCodes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
