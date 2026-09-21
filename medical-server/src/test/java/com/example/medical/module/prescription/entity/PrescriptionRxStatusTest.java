package com.example.medical.module.prescription.entity;

import com.example.medical.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The prescription vocabulary: only ACTIVE is actionable, CANCELLED/COMPLETED are
 * where it stops, and a status the client invents is rejected instead of stored.
 */
class PrescriptionRxStatusTest {

    @Test
    void onlyActiveIsActionable() {
        assertTrue(PrescriptionRxStatus.isActive("active"));
        for (String value : new String[]{"generated", "cancelled", "completed", null}) {
            assertFalse(PrescriptionRxStatus.isActive(value), value + " must not be actionable");
        }
    }

    @Test
    void cancelledAndCompletedAreTerminal() {
        assertTrue(PrescriptionRxStatus.isTerminal("cancelled"));
        assertTrue(PrescriptionRxStatus.isTerminal("completed"));
        assertFalse(PrescriptionRxStatus.isTerminal("active"));
        assertFalse(PrescriptionRxStatus.isTerminal("generated"));
    }

    @Test
    void valuesAreLowercaseAndUnknownOnesAreRejected() {
        assertEquals("active", PrescriptionRxStatus.ACTIVE.value());
        assertEquals(PrescriptionRxStatus.COMPLETED, PrescriptionRxStatus.of("completed"));
        assertNull(PrescriptionRxStatus.of("Completed"), "of() is exact — it guards stored values");
        assertNull(PrescriptionRxStatus.of("dispensed"), "no code path writes 'dispensed'");
        assertEquals(PrescriptionRxStatus.ACTIVE, PrescriptionRxStatus.parse("  Active "),
                "parse() forgives case and space, and stores only the canonical value");

        BusinessException e = assertThrows(BusinessException.class,
                () -> PrescriptionRxStatus.parse("actve"));
        assertEquals(400, e.getCode());
    }
}
