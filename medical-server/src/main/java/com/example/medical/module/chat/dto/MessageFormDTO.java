package com.example.medical.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageFormDTO {

    @NotNull
    private Long receiverId;

    // Required for staff senders (patient ids and staff ids overlap); the
    // patient portal does not send it — the controller defaults it to STAFF.
    private String receiverType;

    @NotBlank
    private String content;
}
