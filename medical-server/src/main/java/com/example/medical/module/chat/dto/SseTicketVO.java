package com.example.medical.module.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SseTicketVO {

    private String ticket;
    private int expiresIn;
}
