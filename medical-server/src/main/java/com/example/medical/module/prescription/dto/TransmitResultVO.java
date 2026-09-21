package com.example.medical.module.prescription.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Draft NCPDP SCRIPT payload produced by the transmit stub. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransmitResultVO {

    private String status;
    private String format;
    private String messageId;
    private String xml;

    public static TransmitResultVO generated(Long prescriptionId, String xml) {
        return new TransmitResultVO("generated", "NCPDP SCRIPT (draft)", "RX-" + prescriptionId, xml);
    }
}
