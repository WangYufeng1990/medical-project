package com.example.medical.module.prescription.service;

import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PharmacyDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NcpdpScriptService {

    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;

    public String generateNewRxXml(Prescription p, List<PrescriptionItem> items, Long pharmacyId) {
        StringBuilder xml = new StringBuilder();
        xml.append("<NewRx xmlns=\"http://www.ncpdp.org/schema/SCRIPT\">\n");
        xml.append("  <Header>\n");
        xml.append("    <MessageId>RX-").append(p.getId()).append("</MessageId>\n");
        xml.append("    <TransmissionDate>").append(java.time.LocalDateTime.now()).append("</TransmissionDate>\n");
        xml.append("  </Header>\n");
        xml.append("  <Prescription>\n");
        xml.append("    <PrescriptionId>").append(p.getId()).append("</PrescriptionId>\n");
        xml.append("    <Diagnosis>").append(escape(p.getDiagnosis())).append("</Diagnosis>\n");
        if (p.getIcd10Codes() != null) {
            xml.append("    <Icd10Codes>").append(p.getIcd10Codes()).append("</Icd10Codes>\n");
        }
        if (p.getControlledSchedule() != null) {
            xml.append("    <ControlledSchedule>").append(p.getControlledSchedule()).append("</ControlledSchedule>\n");
        }
        if (pharmacyId != null) {
            pharmacyDirectoryRepository.findById(pharmacyId).ifPresent(ph ->
                    xml.append("    <PharmacyNpi>").append(ph.getNpi()).append("</PharmacyNpi>\n"));
        }
        for (PrescriptionItem item : items) {
            xml.append("    <Medication>\n");
            xml.append("      <DrugName>").append(escape(item.getDrugName())).append("</DrugName>\n");
            if (item.getNdcCode() != null)
                xml.append("      <NdcCode>").append(item.getNdcCode()).append("</NdcCode>\n");
            if (item.getRxnormCode() != null)
                xml.append("      <RxnormCode>").append(item.getRxnormCode()).append("</RxnormCode>\n");
            xml.append("      <Dosage>").append(escape(item.getDosage())).append("</Dosage>\n");
            xml.append("      <Frequency>").append(escape(item.getFrequency())).append("</Frequency>\n");
            if (item.getSig() != null)
                xml.append("      <Sig>").append(escape(item.getSig())).append("</Sig>\n");
            if (item.getDaysSupply() != null)
                xml.append("      <DaysSupply>").append(item.getDaysSupply()).append("</DaysSupply>\n");
            if (item.getRefills() != null)
                xml.append("      <Refills>").append(item.getRefills()).append("</Refills>\n");
            xml.append("    </Medication>\n");
        }
        xml.append("  </Prescription>\n");
        xml.append("</NewRx>");

        log.info("NCPDP SCRIPT NewRx generated: prescription={} items={}", p.getId(), items.size());
        return xml.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
