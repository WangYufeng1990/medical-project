package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.PrescriptionItem;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrescriptionItemDTO {

    private String drugName;
    private String ndcCode;
    private String rxnormCode;
    private String specification;
    private String dosage;
    private String route;
    private String frequency;
    private String sig;
    private Integer duration;
    private Integer daysSupply;
    private Integer quantity;
    private Integer refills;
    private Integer daw;
    private BigDecimal unitPrice;
    private String notes;

    public PrescriptionItem toEntity() {
        PrescriptionItem item = new PrescriptionItem();
        item.setDrugName(drugName);
        item.setNdcCode(ndcCode);
        item.setRxnormCode(rxnormCode);
        item.setSpecification(specification);
        item.setDosage(dosage);
        item.setRoute(route);
        item.setFrequency(frequency);
        item.setSig(sig);
        item.setDuration(duration);
        item.setDaysSupply(daysSupply);
        item.setQuantity(quantity);
        item.setRefills(refills);
        item.setDaw(daw);
        item.setUnitPrice(unitPrice);
        item.setNotes(notes);
        return item;
    }
}
