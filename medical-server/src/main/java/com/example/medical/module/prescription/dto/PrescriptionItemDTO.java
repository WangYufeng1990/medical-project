package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.PrescriptionItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrescriptionItemDTO {

    @NotBlank(message = "Drug name is required")
    private String drugName;

    private String ndcCode;
    private String rxnormCode;
    private String specification;

    @NotBlank(message = "Dosage is required")
    private String dosage;

    @NotBlank(message = "Route is required")
    private String route;

    @NotBlank(message = "Frequency is required")
    private String frequency;

    private String sig;

    @Positive(message = "Duration must be positive")
    private Integer duration;

    @Positive(message = "Days supply must be positive")
    private Integer daysSupply;

    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @PositiveOrZero(message = "Refills cannot be negative")
    private Integer refills;

    @PositiveOrZero(message = "DAW cannot be negative")
    private Integer daw;

    @PositiveOrZero(message = "Unit price cannot be negative")
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
