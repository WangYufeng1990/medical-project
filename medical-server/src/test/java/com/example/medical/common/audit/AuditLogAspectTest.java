package com.example.medical.common.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Full-System Review III C1: audit detail serialization
 * must never persist passwords, tokens, or clinical free text.
 */
class AuditLogAspectTest {

    static class LoginForm {
        public String username;
        public String password;
        public String refreshToken;
        public String note;
    }

    @Test
    void redactsSensitiveFieldsOnDataObjects() {
        LoginForm form = new LoginForm();
        form.username = "admin";
        form.password = "plain-secret";
        form.refreshToken = "rt-abc-123";
        form.note = "routine note";

        String detail = AuditLogAspect.describeArg(form);

        assertTrue(detail.contains("username=admin"));
        assertTrue(detail.contains("password=[REDACTED]"), detail);
        assertTrue(detail.contains("refreshToken=[REDACTED]"), detail);
        assertTrue(detail.contains("note=[REDACTED]"), detail);
        assertFalse(detail.contains("plain-secret"), detail);
        assertFalse(detail.contains("rt-abc-123"), detail);
    }

    @Test
    void simpleValuesAndNullsArePreserved() {
        assertEquals("null", AuditLogAspect.describeArg(null));
        assertEquals("42", AuditLogAspect.describeArg(42));
        assertEquals("plain string", AuditLogAspect.describeArg("plain string"));
    }

    @Test
    void longValuesAreTruncated() {
        String longValue = "a".repeat(500);
        String detail = AuditLogAspect.describeArg(longValue);
        assertTrue(detail.length() < 100, detail);
    }
}
