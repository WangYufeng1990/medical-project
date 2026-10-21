package com.example.medical;

import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.service.ChatConversationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 54.4 — the notification is a hint about a stored message, never a promise.
 * <p>
 * Three properties, all of which the source had wrong or did not have at all:
 * <ul>
 *   <li>a rolled-back send must not reach the receiver as a {@code new_message}
 *       push (the event used to be published inside the transaction);</li>
 *   <li>a notification that fails must not undo — or appear to undo — a message that
 *       is already committed;</li>
 *   <li>the emergency/help notice is fixed and versioned, and is served without any
 *       model or external service being involved.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatCommitEventIntegrationTest extends IntegrationTestSupport {

    @MockitoSpyBean
    private ChatConversationService conversationService;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    private final List<NewMessageEvent> publishedMessages = new CopyOnWriteArrayList<>();
    private ApplicationListener<ApplicationEvent> recorder;

    private String doctorToken;
    private String patientToken;

    @BeforeAll
    void setUp() throws Exception {
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
        recorder = event -> {
            if (event instanceof PayloadApplicationEvent<?> wrapper
                    && wrapper.getPayload() instanceof NewMessageEvent message) {
                publishedMessages.add(message);
            }
        };
        applicationContext.addApplicationListener(recorder);
    }

    @AfterAll
    void tearDown() {
        if (recorder != null) applicationContext.removeApplicationListener(recorder);
    }

    @BeforeEach
    void resetState() {
        publishedMessages.clear();
        Mockito.reset(conversationService);
    }

    // ── after-commit notification ───────────────────────────────────

    @Test
    void aRolledBackSend_shouldNotNotifyTheReceiver() throws Exception {
        // The thread write fails after the message row was written: the whole
        // transaction rolls back, so nothing was stored and nothing may be pushed.
        doThrow(new IllegalStateException("simulated thread write failure"))
                .when(conversationService).recordMessage(any(), any(), any(), any());
        long before = messagesBetween(2L, "STAFF", 100L, "PATIENT");

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", "54.4 rolled back", "clientMessageId", "rollback-1")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().is5xxServerError());

        assertEquals(before, messagesBetween(2L, "STAFF", 100L, "PATIENT"),
                "the rolled-back message must not be stored");
        assertTrue(publishedMessages.isEmpty(),
                "a rollback must not publish the new-message event: " + publishedMessages);
    }

    @Test
    void theNotificationIsPublishedOnlyAfterTheCommit() throws Exception {
        // A rollback cannot be forced after the last statement of the transaction, so the
        // property is checked where it is decided: when the event arrives, the message
        // must already be visible to another connection. Inside the transaction (the old
        // code) it is not — which is what let a rolled-back send reach the receiver.
        AtomicInteger visibleElsewhere = new AtomicInteger();
        ApplicationListener<ApplicationEvent> probe = event -> {
            if (!(event instanceof PayloadApplicationEvent<?> wrapper)
                    || !(wrapper.getPayload() instanceof NewMessageEvent)) {
                return;
            }
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM message WHERE sender_type = 'STAFF' AND sender_id = 2 "
                                 + "AND client_message_id = 'commit-order-1'");
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                if (rows.getInt(1) > 0) visibleElsewhere.incrementAndGet();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        };
        applicationContext.addApplicationListener(probe);
        try {
            mockMvc.perform(post("/api/v1/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "receiverId", 100, "receiverType", "PATIENT",
                                    "content", "54.4 commit ordering", "clientMessageId", "commit-order-1")))
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk());
        } finally {
            applicationContext.removeApplicationListener(probe);
        }

        assertEquals(1, visibleElsewhere.get(),
                "the new-message event was delivered before the message was committed");
    }

    @Test
    void aSendRolledBackAfterTheNotificationWasRegistered_shouldNotNotify() throws Exception {
        // The notification is registered before the transaction ends, so the property
        // that matters is what happens when the commit itself does not happen: the
        // callback must not run. The stub marks the transaction rollback-only *after*
        // the real thread write, i.e. once the synchronization is already registered.
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return result;
        }).when(conversationService).recordMessage(any(), any(), any(), any());
        long before = messagesBetween(2L, "STAFF", 100L, "PATIENT");

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverId", 100, "receiverType", "PATIENT",
                                "content", "54.4 rollback after registration",
                                "clientMessageId", "rollback-2")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().is5xxServerError());

        assertEquals(before, messagesBetween(2L, "STAFF", 100L, "PATIENT"),
                "a rollback-only transaction stores nothing");
        assertTrue(publishedMessages.isEmpty(),
                "the registered after-commit callback must not run on rollback: " + publishedMessages);
    }

    @Test
    void aFailingNotification_shouldNotUndoTheStoredMessage() throws Exception {
        AtomicInteger failingListenerCalls = new AtomicInteger();
        ApplicationListener<ApplicationEvent> failing = event -> {
            if (event instanceof PayloadApplicationEvent<?> wrapper
                    && wrapper.getPayload() instanceof NewMessageEvent) {
                failingListenerCalls.incrementAndGet();
                throw new IllegalStateException("simulated notification failure");
            }
        };
        applicationContext.addApplicationListener(failing);
        long before = messagesBetween(2L, "STAFF", 100L, "PATIENT");
        try {
            mockMvc.perform(post("/api/v1/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "receiverId", 100, "receiverType", "PATIENT",
                                    "content", "54.4 notification failure",
                                    "clientMessageId", "notify-failure-1")))
                            .header("Authorization", "Bearer " + doctorToken))
                    .andExpect(status().isOk());
        } finally {
            applicationContext.removeApplicationListener(failing);
        }

        assertEquals(before + 1, messagesBetween(2L, "STAFF", 100L, "PATIENT"),
                "the message was committed before the notification ran and must survive it");
        assertTrue(failingListenerCalls.get() > 0, "the notification was attempted");
    }

    // ── the fixed safety notice ─────────────────────────────────────

    @Test
    void theSafetyNoticeIsFixedVersionedAndServedToBothPortals() throws Exception {
        JsonNode staff = safetyNotice(doctorToken);
        JsonNode patient = safetyNotice(patientToken);

        assertEquals(staff, patient, "both portals see the same reviewed notice");
        assertEquals(1, staff.get("version").asInt(), "the notice is versioned");
        assertTrue(staff.get("emergencyNotice").asText().contains("911"),
                "the notice must say what to do in an emergency: " + staff.get("emergencyNotice"));
        assertTrue(staff.get("responseNotice").asText().contains("may take time"),
                "the notice must not promise a real-time answer: " + staff.get("responseNotice"));
        assertFalse(staff.get("responseNotice").asText().contains("24/7"),
                "and it must not imply round-the-clock coverage");
    }

    @Test
    void theSafetyNoticeRequiresAnAuthenticatedIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/messages/safety-notice"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/patient/me/messages/safety-notice"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────────────

    private JsonNode safetyNotice(String token) throws Exception {
        String path = token.equals(patientToken)
                ? "/api/v1/patient/me/messages/safety-notice"
                : "/api/v1/messages/safety-notice";
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long messagesBetween(long senderId, String senderType, long receiverId, String receiverType) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message WHERE sender_id = ? AND sender_type = ? "
                        + "AND receiver_id = ? AND receiver_type = ?",
                Integer.class, senderId, senderType, receiverId, receiverType);
        return found == null ? 0 : found;
    }
}
