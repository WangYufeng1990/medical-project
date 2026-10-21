package com.example.medical;

import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 54.3 — a send is idempotent on the client's message id.
 * <p>
 * A retry after a timeout, a double click and a flaky network all re-send the same
 * content. The contract: the same {@code clientMessageId} from the same sender
 * returns the message that already exists (no second row, no second thread revision,
 * no second notification), while the same key with a different payload is a
 * conflict rather than a silent overwrite or a duplicate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageIdempotencyIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private final List<NewMessageEvent> publishedMessages = new CopyOnWriteArrayList<>();
    private ApplicationListener<ApplicationEvent> messageListener;

    private String doctorToken;
    private String patientToken;

    @BeforeAll
    void setUp() throws Exception {
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
        messageListener = new ApplicationListener<ApplicationEvent>() {
            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                if (event instanceof PayloadApplicationEvent<?> wrapper
                        && wrapper.getPayload() instanceof NewMessageEvent newMessage) {
                    publishedMessages.add(newMessage);
                }
            }
        };
        applicationContext.addApplicationListener(messageListener);
    }

    @AfterAll
    void tearDown() {
        if (messageListener != null) applicationContext.removeApplicationListener(messageListener);
    }

    // The messages this class sends are left in place: deleting them would leave the
    // seeded thread pointing at rows that no longer exist, and the counters are
    // asserted as deltas, so the history is harmless. The unique keys are per test.
    @BeforeEach
    void resetRecorder() {
        publishedMessages.clear();
    }

    @Test
    void repeatedSendWithTheSameKey_shouldReturnTheSameMessageStoredOnce() throws Exception {
        long pairBefore = pairCount();
        long countBefore = threadMessageCount();
        long revisionBefore = threadRevision();
        long before = messagesWithKey("retry-1");

        MvcResult first = sendAsStaff(doctorToken, "retry-1", 100L, "idempotent content")
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstMessage = data(first);

        MvcResult second = sendAsStaff(doctorToken, "retry-1", 100L, "idempotent content")
                .andExpect(status().isOk())
                .andReturn();
        JsonNode secondMessage = data(second);

        assertEquals(firstMessage.get("id").asLong(), secondMessage.get("id").asLong(),
                "a retry must return the message that already exists");
        assertEquals("retry-1", secondMessage.get("clientMessageId").asText());
        assertEquals(before + 1, messagesWithKey("retry-1"), "the retry must not store a second row");
        assertEquals(pairBefore + 1, pairCount(), "the retry must not add a message to the thread either");
        assertEquals(countBefore + 1, threadMessageCount(),
                "the thread counts the message once, not twice");
        assertEquals(revisionBefore + 1, threadRevision(),
                "the thread's revision moves once, not twice");
        assertEquals(1, publishedMessages.size(), "the retry must not publish a second event");
    }

    @Test
    void sameKeyWithDifferentContent_shouldConflict() throws Exception {
        MvcResult first = sendAsStaff(doctorToken, "conflict-content", 100L, "the original message")
                .andExpect(status().isOk())
                .andReturn();
        long before = messagesWithKey("conflict-content");

        sendAsStaff(doctorToken, "conflict-content", 100L, "a different message")
                .andExpect(status().isConflict());

        assertEquals(before, messagesWithKey("conflict-content"),
                "a conflicting payload must not add a row");
        assertEquals(1, publishedMessages.size());
    }

    @Test
    void sameKeyWithDifferentReceiver_shouldConflict() throws Exception {
        MvcResult first = sendAsStaff(doctorToken, "conflict-receiver", 101L, "same content")
                .andExpect(status().isOk())
                .andReturn();
        long before = messagesWithKey("conflict-receiver");

        sendAsStaff(doctorToken, "conflict-receiver", 100L, "same content")
                .andExpect(status().isConflict());

        assertEquals(before, messagesWithKey("conflict-receiver"));
    }

    @Test
    void theSameKeyFromDifferentSenders_shouldNotCollide() throws Exception {
        // The key is scoped to the sender: a patient and a doctor may both use
        // "shared-key" for their own messages.
        long before = messagesWithKey("shared-key");

        MvcResult fromStaff = sendAsStaff(doctorToken, "shared-key", 100L, "staff side")
                .andExpect(status().isOk())
                .andReturn();

        MvcResult fromPatient = mockMvc.perform(post("/api/v1/patient/me/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 2, "content", "patient side", "clientMessageId", "shared-key")))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(before + 2, messagesWithKey("shared-key"),
                "two senders using one key are two messages");
        assertNotEquals(data(fromStaff).get("id").asLong(), data(fromPatient).get("id").asLong());
    }

    @Test
    void withoutAKey_everySendIsStored() throws Exception {
        // The key is optional: an API client that does not send one keeps the old
        // behaviour, and two keyless rows must not collide on the unique index.
        long before = messageRepository.count();

        MvcResult first = sendAsStaff(doctorToken, null, 100L, "no key at all")
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = sendAsStaff(doctorToken, null, 100L, "no key at all")
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(before + 2, messageRepository.count());
        assertNotEquals(data(first).get("id").asLong(), data(second).get("id").asLong());
    }

    @Test
    void concurrentRepeatsOfOneKey_shouldEndUpAsOneMessage() throws Exception {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CyclicBarrier allReady = new CyclicBarrier(attempts);
        List<Future<Long>> results = new ArrayList<>();
        long before = messagesWithKey("racy-key");
        long countBefore = threadMessageCount();
        long revisionBefore = threadRevision();
        try {
            for (int i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    allReady.await(10, TimeUnit.SECONDS);
                    MvcResult result = sendAsStaff(doctorToken, "racy-key", 100L, "racy content")
                            .andExpect(status().isOk())
                            .andReturn();
                    return data(result).get("id").asLong();
                }));
            }
            List<Long> ids = new ArrayList<>();
            for (Future<Long> result : results) {
                ids.add(result.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, new java.util.LinkedHashSet<>(ids).size(),
                    "every concurrent attempt must resolve to the one stored message: " + ids);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(before + 1, messagesWithKey("racy-key"));
        assertEquals(countBefore + 1, threadMessageCount(),
                "eight concurrent attempts count as one message");
        assertEquals(revisionBefore + 1, threadRevision(),
                "and the thread's revision moves once");
        assertEquals(1, publishedMessages.size(), "one message means one event");
    }

    // ── helpers ─────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions sendAsStaff(
            String token, String clientMessageId, long receiverId, String content) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverId", receiverId);
        body.put("receiverType", "PATIENT");
        body.put("content", content);
        if (clientMessageId != null) body.put("clientMessageId", clientMessageId);
        return mockMvc.perform(post("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long pairCount() {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message WHERE sender_type = 'STAFF' AND sender_id = 2 "
                        + "AND receiver_type = 'PATIENT' AND receiver_id = 100", Integer.class);
        return found == null ? 0 : found;
    }

    private long threadMessageCount() {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT message_count FROM chat_conversation WHERE "
                        + "(party_a_type = 'PATIENT' AND party_a_id = 100) "
                        + "OR (party_b_type = 'PATIENT' AND party_b_id = 100)", Integer.class);
        return found == null ? 0 : found;
    }

    private long threadRevision() {
        Long found = jdbcTemplate.queryForObject(
                "SELECT revision FROM chat_conversation WHERE "
                        + "(party_a_type = 'PATIENT' AND party_a_id = 100) "
                        + "OR (party_b_type = 'PATIENT' AND party_b_id = 100)", Long.class);
        return found == null ? 0 : found;
    }

    private long messagesWithKey(String clientMessageId) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message WHERE client_message_id = ?", Integer.class, clientMessageId);
        return found == null ? 0 : found;
    }
}
