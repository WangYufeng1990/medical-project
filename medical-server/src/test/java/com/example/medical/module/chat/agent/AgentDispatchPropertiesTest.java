package com.example.medical.module.chat.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Round 56.1 review: the dispatch timeout has to cover the model call plus the callback, and the
 * lease has to outlive the timeout. An incoherent combination is refused at startup rather than
 * discovered as a duplicate dispatch later.
 */
class AgentDispatchPropertiesTest {

    @Test
    void theDefaultsAreCoherent() {
        assertDoesNotThrow(() -> new AgentDispatchProperties().validate());
    }

    @Test
    void aTimeoutLongerThanTheLeaseIsRefused() {
        AgentDispatchProperties properties = new AgentDispatchProperties();
        properties.setRequestTimeout(Duration.ofSeconds(120));
        properties.setLease(Duration.ofSeconds(60));

        IllegalStateException error = assertThrows(IllegalStateException.class, properties::validate);
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("must exceed"));
    }

    @Test
    void aLeaseLongerThanTheDeadlineIsRefused() {
        AgentDispatchProperties properties = new AgentDispatchProperties();
        properties.setLease(Duration.ofMinutes(45));
        properties.setDeadline(Duration.ofMinutes(30));

        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void theDefaultTimeoutCoversTheDocumentedModelTimeout() {
        AgentDispatchProperties properties = new AgentDispatchProperties();

        org.junit.jupiter.api.Assertions.assertTrue(
                properties.getRequestTimeout().compareTo(Duration.ofSeconds(30)) > 0,
                "the Java timeout must cover the service's 30 s model call");
        org.junit.jupiter.api.Assertions.assertTrue(
                properties.getLease().compareTo(properties.getRequestTimeout()) > 0);
    }
}
