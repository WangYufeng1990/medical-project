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

    public static PrescriptionItemVO fromEntity(PrescriptionItem item) {
        return new PrescriptionItemVO(
                item.getId(),
                item.getDrugName(),
                item.getNdcCode(),
                item.getRxnormCode(),
                item.getSpecification(),
                item.getDosage(),
                item.getRoute(),
                item.getFrequency(),
                item.getSig(),
                item.getDuration(),
                item.getDaysSupply(),
                item.getQuantity(),
                item.getRefills(),
                item.getDaw(),
                item.getUnitPrice(),
                item.getNotes());
    }
}
