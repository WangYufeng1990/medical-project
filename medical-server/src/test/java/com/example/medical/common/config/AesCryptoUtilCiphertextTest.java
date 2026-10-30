package com.example.medical.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 57.2 review: "is this already ciphertext" decides whether the backfill touches a value, so a
 * prose value that merely starts with "01" must not be mistaken for one.
 */
class AesCryptoUtilCiphertextTest {

    @Test
    void aProseValueThatStartsLikeACiphertextIsNotOne() {
        // 122 characters, starts with 01, contains a non-hex letter.
        String prose = "01" + "z".repeat(120);

        assertFalse(AesCryptoUtil.isCiphertext(prose));
    }

    @Test
    void realCiphertextIsRecognised() {
        // The key is static, and Surefire reuses the JVM: leaving this test key installed would make
        // every later class decrypt the application's rows with the wrong key (Round 57.2 review).
        AesCryptoUtil.KeySnapshot snapshot = AesCryptoUtil.snapshotKeysForTest();
        try {
            AesCryptoUtil.initializeForTest("a-test-key-for-ciphertext-detection-0123456789");

            String encrypted = AesCryptoUtil.encrypt("Legacy plaintext draft");

            assertTrue(AesCryptoUtil.isCiphertext(encrypted));
            assertFalse(AesCryptoUtil.isCiphertext("Legacy plaintext draft"));
            assertFalse(AesCryptoUtil.isCiphertext("01"));
        } finally {
            AesCryptoUtil.restoreKeysForTest(snapshot);
        }
    }
}
