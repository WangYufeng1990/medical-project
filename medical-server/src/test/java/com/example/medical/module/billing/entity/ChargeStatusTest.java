package com.example.medical.module.billing.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The charge vocabulary: DRAFT is the only status a charge can be converted
 * from, and the values are the ones the column has always held.
 */
class ChargeStatusTest {

    @Test
    void onlyDraftIsConvertible() {
        assertTrue(ChargeStatus.isConvertible("DRAFT"));
        assertFalse(ChargeStatus.isConvertible("BILLED"), "a billed charge must not be billed twice");
        assertFalse(ChargeStatus.isConvertible(null), "a charge with no status has nothing to convert");
        assertFalse(ChargeStatus.isConvertible("draft"), "the wire values are upper case");
    }

    @Test
    void ofIsExact() {
        assertEquals(ChargeStatus.BILLED, ChargeStatus.of("BILLED"));
        assertNull(ChargeStatus.of("billed"), "of() is exact — it guards stored values");
        assertNull(ChargeStatus.of(null));
    }

    @Test
    void everyStatusHasTheValueTheColumnStores() {
        assertEquals("DRAFT", ChargeStatus.DRAFT.value());
        assertEquals("BILLED", ChargeStatus.BILLED.value());
        assertTrue(ChargeStatus.DRAFT.matches(ChargeStatus.DRAFT.value()),
                "matches() pairs with value() — the guard reads stored data");
        assertFalse(ChargeStatus.DRAFT.matches(ChargeStatus.BILLED.value()));
    }
}
