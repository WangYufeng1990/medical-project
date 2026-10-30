package com.example.medical.module.chat.dto.agent;

import lombok.Data;

/**
 * What the Java side knows about a run's result (Round 57.3).
 * <p>
 * A recovery path needs this because an HTTP 403/409 from the result endpoint means "not accepted
 * now", not "not accepted ever": the capability is revoked once a result is stored, so a replay gets
 * 403 while the work is finished. Only this receipt can tell the two apart.
 */
@Data
public class RunReceiptVO {

    private Long runId;
    private Long generation;
    private String state;
    private boolean hasResult;
    private Long resultGeneration;
    private boolean accepted;
}
