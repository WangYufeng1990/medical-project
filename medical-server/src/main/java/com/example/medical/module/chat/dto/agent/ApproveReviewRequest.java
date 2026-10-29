package com.example.medical.module.chat.dto.agent;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Approve-and-send (Round 57.2); an optional edit replaces the draft. */
@Data
public class ApproveReviewRequest {

    @Size(max = 2000)
    private String finalText;
}
