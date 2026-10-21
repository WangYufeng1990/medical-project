package com.example.medical.module.chat.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * The fixed, reviewed emergency/help notice both portals show (Round 54.4). It carries
 * its own version because the wording is a reviewed artefact: changing it changes the
 * version, and nothing about it depends on a model being reachable.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SafetyNoticeVO {

    private int version;
    private String emergencyNotice;
    private String responseNotice;

    public static SafetyNoticeVO of(int version, String emergencyNotice, String responseNotice) {
        return new SafetyNoticeVO(version, emergencyNotice, responseNotice);
    }
}
