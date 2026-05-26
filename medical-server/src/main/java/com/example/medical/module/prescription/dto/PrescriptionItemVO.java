package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.PrescriptionItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrescriptionItemVO {

    private Long id;
    private String drugName;
    private String specification;
    private String dosage;
    private String frequency;
    private Integer duration;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String notes;

    public static PrescriptionItemVO fromEntity(PrescriptionItem item) {
        return new PrescriptionItemVO(item.getId(), item.getDrugName(),
                item.getSpecification(), item.getDosage(), item.getFrequency(),
                item.getDuration(), item.getQuantity(), item.getUnitPrice(),
                item.getNotes());
    }
}
