package com.example.medical.common.audit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Result of walking the audit hash chain: which row, if any, breaks it. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntegrityReportVO {

    private boolean intact;
    private long brokenRowId;

    public static IntegrityReportVO of(Long brokenRowId) {
        return new IntegrityReportVO(brokenRowId == null, brokenRowId == null ? -1L : brokenRowId);
    }
}
