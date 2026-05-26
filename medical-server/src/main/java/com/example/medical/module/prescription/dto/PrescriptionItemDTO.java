package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.PrescriptionItem;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrescriptionItemDTO {

    private String drugName;
    private String specification;
    private String dosage;
    private String frequency;
    private Integer duration;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String notes;

    public PrescriptionItem toEntity() {
        PrescriptionItem item = new PrescriptionItem();
        item.setDrugName(drugName);
        item.setSpecification(specification);
        item.setDosage(dosage);
        item.setFrequency(frequency);
        item.setDuration(duration);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setNotes(notes);
        return item;
    }
}
