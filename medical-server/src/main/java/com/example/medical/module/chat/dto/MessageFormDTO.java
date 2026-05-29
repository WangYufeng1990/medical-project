package com.example.medical.module.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageFormDTO {

    @NotNull
    private Long receiverId;

    @NotBlank
    private String content;
}
