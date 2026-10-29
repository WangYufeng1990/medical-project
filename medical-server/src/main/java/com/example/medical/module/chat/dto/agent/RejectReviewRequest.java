package com.example.medical.module.chat.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Refusing a draft (Round 57.2); the reason is kept for audit. */
@Data
public class RejectReviewRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
