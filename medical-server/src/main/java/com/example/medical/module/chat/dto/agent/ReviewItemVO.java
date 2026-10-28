package com.example.medical.module.chat.dto.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** One row of the staff review queue (Round 57.1). */
@Data
public class ReviewItemVO {

    private Long messageId;
    private Long patientId;
    /** Null for a human-only item: there is no draft to review. */
    private Long runId;
    private String state;
    private String intent;
    private String risk;
    private boolean requiresClinician;
    private String draft;
    private List<Citation> citations = new ArrayList<>();
    private Validation validation;
    private Long claimedBy;
    private boolean claimedByMe;
    /** Why a human-only item is in the queue: NO_RUN, RUN_FAILED, RUN_PENDING or NO_RESULT. */
    private String reason;

    @Data
    public static class Citation {
        private String chunkId;
        private String documentId;
        private Long documentVersion;
        private String title;
        private List<String> headingPath = new ArrayList<>();
        private String contentHash;
    }

    @Data
    public static class Validation {
        private boolean ok;
        private String kind;
        private List<String> reasons = new ArrayList<>();
        private List<String> rejectedReasons = new ArrayList<>();
    }
}
