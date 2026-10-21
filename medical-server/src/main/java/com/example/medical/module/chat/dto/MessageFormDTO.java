package com.example.medical.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageFormDTO {

    @NotNull
    private Long receiverId;

    // Required for staff senders (patient ids and staff ids overlap); the
    // patient portal does not send it — the controller defaults it to STAFF.
    private String receiverType;

    // Optional: a client that supplies one gets idempotent retries; one that does
    // not keeps the previous behaviour (every send is stored).
    @Size(max = 100, message = "clientMessageId must be at most 100 characters")
    private String clientMessageId;

    @NotBlank
    private String content;
}
