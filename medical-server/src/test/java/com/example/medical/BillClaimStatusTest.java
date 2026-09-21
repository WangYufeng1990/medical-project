package com.example.medical.module.billing.entity;

import com.example.medical.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The claim-status vocabulary itself: paying is only legal from PENDING (the rule
 * the billing UI used to disagree with), PAID/DENIED are terminal, and an unknown
 * filter value is rejected rather than silently matching nothing.
 */
class BillClaimStatusTest {

    @Test
    void onlyPendingIsPayable() {
        assertTrue(BillClaimStatus.isPayable("PENDING"));
        for (String value : new String[]{"DRAFT", "SUBMITTED", "PAID", "DENIED", null}) {
            assertFalse(BillClaimStatus.isPayable(value), value + " must not be payable");
        }
    }

    @Test
    void paidAndDeniedAreTerminal() {
        assertTrue(BillClaimStatus.isTerminal("PAID"));
        assertTrue(BillClaimStatus.isTerminal("DENIED"));
        assertFalse(BillClaimStatus.isTerminal("PENDING"));
        assertFalse(BillClaimStatus.isTerminal(null));
    }

    @Test
    void unknownStatusIsRejected() {
        assertEquals(BillClaimStatus.PAID, BillClaimStatus.of("PAID"));
        assertNull(BillClaimStatus.of("paid"), "the wire values are upper case");
        assertNull(BillClaimStatus.of(null));
        BusinessException e = assertThrows(BusinessException.class,
                () -> BillClaimStatus.parse("PENDNG"));
        assertEquals(400, e.getCode());
    }
}
