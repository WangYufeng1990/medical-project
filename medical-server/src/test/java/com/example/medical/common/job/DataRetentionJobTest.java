package com.example.medical.common.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.medical.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Round 59.3: retention archives old audit rows on H2 and never deletes them. */
class DataRetentionJobTest extends IntegrationTestSupport {

    @Autowired
    private DataRetentionJob job;

    @Test
    void old_rows_are_archived_and_new_rows_are_kept() {
        jdbcTemplate.update("""
                INSERT INTO audit_log (username, module, action, detail, create_time, archived, row_hash)
                VALUES ('retention-old', 'patient', 'READ', 'old', DATEADD('DAY', -4000, CURRENT_TIMESTAMP), 0, 'a')
                """);
        jdbcTemplate.update("""
                INSERT INTO audit_log (username, module, action, detail, create_time, archived, row_hash)
                VALUES ('retention-new', 'patient', 'READ', 'new', CURRENT_TIMESTAMP, 0, 'b')
                """);

        job.purgeExpiredAuditLogs();

        Map<String, Object> old = row("retention-old");
        Map<String, Object> recent = row("retention-new");
        assertThat(old.get("ARCHIVED")).isEqualTo(1);
        assertThat(recent.get("ARCHIVED")).isEqualTo(0);
        assertThat(count()).isEqualTo(2L);
    }

    private Map<String, Object> row(String username) {
        return jdbcTemplate.queryForMap(
                "SELECT archived FROM audit_log WHERE username = ?", username);
    }

    private Long count() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE username LIKE 'retention-%'", Long.class);
    }
}
