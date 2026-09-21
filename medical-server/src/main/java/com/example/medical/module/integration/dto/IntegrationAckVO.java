package com.example.medical.module.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Acknowledgement for an inbound interface message. The lab-results call reports
 * how many rows it created; the ADT call has no count, so that key is omitted
 * rather than sent as null (an interface engine parses this).
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrationAckVO {

    private String status;
    private String sourceMessageId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer recordsCreated;

    public static IntegrationAckVO accepted(String sourceMessageId) {
        return new IntegrationAckVO("ACK", sourceMessageId, null);
    }

    public static IntegrationAckVO accepted(String sourceMessageId, int recordsCreated) {
        return new IntegrationAckVO("ACK", sourceMessageId, recordsCreated);
    }
}
