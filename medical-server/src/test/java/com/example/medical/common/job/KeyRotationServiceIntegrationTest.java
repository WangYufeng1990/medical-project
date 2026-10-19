package com.example.medical.common.job;

import com.example.medical.IntegrationTestSupport;
import com.example.medical.common.config.AesCryptoUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Key rotation must scan to the end of a column and tell the truth about what it left
 * behind (R53-03).
 * <p>
 * The scan used to stop at the first batch that needed no rewrite — so after an
 * interrupted rotation, a column whose first rows were already migrated ended the scan
 * there, leaving later rows on the previous key. It then reported every column as
 * having zero remaining and marked the rotation complete, which retires the old key and
 * makes those rows unreadable.
 * <p>
 * The key state is process-wide static; {@code AesCryptoUtil} exposes snapshot and
 * restore for exactly this, and every case restores it.
 */
class KeyRotationServiceIntegrationTest extends IntegrationTestSupport {

    private static final String CURRENT_KEY = "rotation-test-current-key";
    private static final String PREVIOUS_KEY = "rotation-test-previous-key";
    private static final KeyRotationService.TableColumn NOTES =
            new KeyRotationService.TableColumn("problem", "notes");
    private static final long FIRST_ID = 9_001L;

    @Autowired
    private KeyRotationService rotation;

    private AesCryptoUtil.KeySnapshot keys;

    @BeforeEach
    void armRotation() {
        keys = AesCryptoUtil.snapshotKeysForTest();
        rotation.setPauseMillisForTest(0);
        deleteProbeRows();
    }

    @AfterEach
    void restoreKeys() {
        deleteProbeRows();
        AesCryptoUtil.restoreKeysForTest(keys);
    }

    // ──────────────────────────────────────────────────────

    /**
     * The finding: a batch already on the new key must not end the scan for the row
     * behind it that is still on the old one.
     */
    @Test
    void aBatchThatNeedsNoRewrite_shouldNotEndTheScan() {
        long alreadyMigrated = FIRST_ID;
        long stillLegacy = FIRST_ID + 1;
        insertProblem(alreadyMigrated, ciphertextUnderCurrentKey("fresh note"));
        insertProblem(stillLegacy, ciphertextUnderPreviousKey("legacy note"));
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);

        int remaining = rotation.migrateColumn(NOTES, 1);

        assertEquals(0, remaining, "nothing is left on the previous key once the scan reaches the end");
        assertFalse(AesCryptoUtil.isEncryptedWithPreviousKey(notesOf(stillLegacy)),
                "the legacy row must be migrated even though the batch before it needed no rewrite");
    }

    /** The reporting primitive: a scan's claim about what it left must be countable. */
    @Test
    void countPreviousKeyRows_shouldReportWhatIsStillOnTheOldKey() {
        insertProblem(FIRST_ID, ciphertextUnderPreviousKey("legacy note"));
        insertProblem(FIRST_ID + 1, ciphertextUnderCurrentKey("fresh note"));
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);

        assertEquals(1, rotation.countPreviousKeyRows(NOTES));
    }

    /**
     * A clinical edit that lands between the SELECT and the UPDATE must survive: the
     * rotation rewrites ciphertext, it does not overwrite content.
     */
    @Test
    void aRowEditedSinceItWasRead_shouldNotBeOverwritten() {
        String readCiphertext = ciphertextUnderPreviousKey("original note");
        insertProblem(FIRST_ID, readCiphertext);
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);

        String editedMeanwhile = ciphertextUnderCurrentKey("edited clinically while rotating");
        jdbcTemplate.update("UPDATE problem SET notes = ? WHERE id = ?", editedMeanwhile, FIRST_ID);

        int updated = rotation.migrateRow("problem", "notes", FIRST_ID, readCiphertext,
                ciphertextUnderCurrentKey("rotated note"));

        assertEquals(0, updated, "the ciphertext that was read is no longer there");
        assertEquals(editedMeanwhile, notesOf(FIRST_ID), "the concurrent edit survives");
    }

    /** A whole run over a clean database still completes — and migrates what it finds. */
    @Test
    void aFullRun_shouldMigrateAndComplete() {
        insertProblem(FIRST_ID, ciphertextUnderPreviousKey("legacy note"));
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);

        rotation.resetStateForTest();
        rotation.runBatchRotation();

        assertTrue(rotation.isComplete());
        assertFalse(AesCryptoUtil.isEncryptedWithPreviousKey(notesOf(FIRST_ID)));
    }

    // ──────────────────────────────────────────────────────

    private void insertProblem(long id, String notes) {
        jdbcTemplate.update("INSERT INTO problem (id, patient_id, status, notes) VALUES (?,?,?,?)",
                id, 100L, "ACTIVE", notes);
    }

    private String notesOf(long id) {
        return jdbcTemplate.queryForObject("SELECT notes FROM problem WHERE id = ?", String.class, id);
    }

    private void deleteProbeRows() {
        jdbcTemplate.update("DELETE FROM problem WHERE id >= ?", FIRST_ID);
    }

    /** A ciphertext the previous key can decrypt and the current key cannot. */
    private String ciphertextUnderPreviousKey(String plaintext) {
        AesCryptoUtil.initializeForTest(PREVIOUS_KEY);
        String ciphertext = AesCryptoUtil.encrypt(plaintext);
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);
        return ciphertext;
    }

    private String ciphertextUnderCurrentKey(String plaintext) {
        AesCryptoUtil.initializeForTest(CURRENT_KEY, PREVIOUS_KEY);
        return AesCryptoUtil.encrypt(plaintext);
    }
}
