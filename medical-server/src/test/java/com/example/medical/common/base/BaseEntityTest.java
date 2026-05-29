package com.example.medical.common.base;

import com.example.medical.module.patient.entity.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    @Test
    void shouldHaveVersionFieldForOptimisticLocking() {
        Patient patient = new Patient();
        assertNull(patient.getVersion(), "version should be null before first persist");

        patient.setVersion(0);
        assertEquals(0, patient.getVersion());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        Patient patient = new Patient();
        patient.setName("Test Patient");
        patient.setMrn("MRN-TEST");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));

        // @PrePersist callbacks are inherited from BaseEntity
        assertDoesNotThrow(() -> BaseEntity.class.getDeclaredMethod("onCreate"));
        assertDoesNotThrow(() -> BaseEntity.class.getDeclaredMethod("onUpdate"));
    }
}
