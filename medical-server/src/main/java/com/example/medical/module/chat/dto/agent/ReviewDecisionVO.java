package com.example.medical.module.chat.dto.agent;

import lombok.Data;

/** The outcome of a review decision (Round 57.2). */
@Data
public class ReviewDecisionVO {

    private Long runId;
    private String decision;
    private Long actorId;
    private Long publishedMessageId;
    private String finalText;
    private boolean edited;
    private boolean noticeAppended;
}
