package com.example.medical.module.system.entity;

import com.example.medical.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 0/1 flag shared by sys_user, sys_role and sys_menu: two values, and both
 * of them decide something (login refusal, force-logout).
 */
class EnabledStatusTest {

    @Test
    void onlyZeroIsDisabled() {
        assertTrue(EnabledStatus.isDisabled(0));
        assertFalse(EnabledStatus.isDisabled(1));
        assertFalse(EnabledStatus.isDisabled(null), "a missing status is not a disabled account");
        assertTrue(EnabledStatus.isEnabled(1));
        assertFalse(EnabledStatus.isEnabled(null), "and it is not an enabled one either");
        assertFalse(EnabledStatus.isDisabled(7), "an unknown value is neither state");
    }

    @Test
    void ofIsExactAndParseRejectsUnknownValues() {
        assertEquals(EnabledStatus.DISABLED, EnabledStatus.of(0));
        assertEquals(EnabledStatus.ENABLED, EnabledStatus.of(1));
        assertNull(EnabledStatus.of(2), "of() is exact — it guards stored values");
        assertNull(EnabledStatus.of(null));

        BusinessException e = assertThrows(BusinessException.class, () -> EnabledStatus.parse(7));
        assertEquals(400, e.getCode());
        assertEquals("Unknown status: 7", e.getMessage());
    }

    @Test
    void anAbsentStatusMeansEnabled() {
        assertEquals(EnabledStatus.ENABLED, EnabledStatus.parse(null),
                "the value the role and menu payloads have always defaulted to");
        assertEquals(1, EnabledStatus.ENABLED.value());
        assertEquals(0, EnabledStatus.DISABLED.value());
        assertTrue(EnabledStatus.ENABLED.matches(EnabledStatus.ENABLED.value()),
                "matches() pairs with value() — the guard reads stored data");
    }
}
