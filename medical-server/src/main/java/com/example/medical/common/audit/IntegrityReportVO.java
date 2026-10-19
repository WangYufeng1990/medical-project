package com.example.medical.common.audit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Result of walking the audit hash chain: which row, if any, breaks it, and how. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrityReportVO {

    private boolean intact;
    private long brokenRowId;
    private String breakReason;

    public static IntegrityReportVO of(AuditLogService.ChainBreak broken) {
        if (broken == null) {
            return new IntegrityReportVO(true, -1L, null);
        }
        return new IntegrityReportVO(false, broken.rowId(), broken.reason().name());
    }
}
