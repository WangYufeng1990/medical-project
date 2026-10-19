package com.example.medical;

import com.example.medical.module.chat.entity.ChatConversation;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.repository.ChatConversationRepository;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.chat.service.ChatConversationBackfill;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 54.2 — the conversation list is a database query, not a window over the
 * newest 20 messages.
 * <p>
 * The list used to be built by loading the 20 most recent messages and grouping
 * them in memory, so a busy thread hid every quieter one and "page 2" only
 * paginated whatever had fitted in that window. These tests drive the public
 * endpoint only, so the red state below is behaviour, not a missing API.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatConversationIntegrationTest extends IntegrationTestSupport {

    private static final int BUSY_MESSAGES = 22;

    /** The v3 message table, i.e. the reviewed schema this round upgrades from. */
    private static final String V3_MESSAGE_TABLE = "CREATE TABLE IF NOT EXISTS message ("
            + "id BIGINT NOT NULL AUTO_INCREMENT, sender_id BIGINT NOT NULL, sender_type VARCHAR(10) NOT NULL, "
            + "receiver_id BIGINT NOT NULL, receiver_type VARCHAR(10) NOT NULL, content TEXT NOT NULL, "
            + "is_read TINYINT DEFAULT 0, create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, is_deleted TINYINT NOT NULL DEFAULT 0, "
            + "version INT NOT NULL DEFAULT 0, PRIMARY KEY (id))";

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatConversationBackfill backfill;

    private final List<Long> createdPatientIds = new ArrayList<>();

    private String doctorToken;
    private String adminToken;

    @BeforeAll
    void authenticate() throws Exception {
        doctorToken = login("doctor1", "doctor123");
        adminToken = login("admin", "admin123");
    }

    @AfterEach
    void cleanUpFixtures() {
        // Only test-owned threads are removed: the messages this class sends to the
        // seeded patients stay as history, so a later class still sees the seeded
        // threads it expects (and no conversation row is left pointing at a message
        // that no longer exists).
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM chat_conversation WHERE "
                    + "(party_a_type = 'PATIENT' AND party_a_id = ?) "
                    + "OR (party_b_type = 'PATIENT' AND party_b_id = ?)", patientId, patientId);
            jdbcTemplate.update("DELETE FROM message WHERE "
                    + "(receiver_type = 'PATIENT' AND receiver_id = ?) "
                    + "OR (sender_type = 'PATIENT' AND sender_id = ?)", patientId, patientId);
            jdbcTemplate.update("DELETE FROM appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient_auth WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        createdPatientIds.clear();
    }

    @Test
    void busyThread_shouldNotHideAnOlderOne() throws Exception {
        send(doctorToken, 101L, "PATIENT", "older thread with patient 101");
        for (int i = 0; i < BUSY_MESSAGES; i++) {
            send(doctorToken, 100L, "PATIENT", "busy thread message " + i);
        }

        JsonNode records = conversations(doctorToken, 1, 10);

        assertTrue(hasPartner(records, "PATIENT", 101L),
                "patient 101's thread must still be listed after 22 newer messages in another thread");
        assertEquals(1, countPartner(records, "PATIENT", 100L),
                "22 messages in one thread are one conversation, not 22");
        assertEquals(100L, records.get(0).get("partnerId").asLong(),
                "the most recently active thread is listed first");
        assertEquals("busy thread message " + (BUSY_MESSAGES - 1), records.get(0).get("lastMessage").asText());
    }

    @Test
    void pages_shouldCoverEveryThreadOnce_andStayStable() throws Exception {
        send(doctorToken, 101L, "PATIENT", "page fixture 101");
        send(doctorToken, 1L, "STAFF", "page fixture colleague");
        for (int i = 0; i < BUSY_MESSAGES; i++) {
            send(doctorToken, 100L, "PATIENT", "page fixture busy " + i);
        }

        List<String> firstPage = partners(conversations(doctorToken, 1, 2));
        List<String> secondPage = partners(conversations(doctorToken, 2, 2));
        List<String> all = new ArrayList<>(firstPage);
        all.addAll(secondPage);

        assertEquals(2, firstPage.size(), "a two-row page must be full while more threads exist");
        assertEquals(all.size(), new java.util.LinkedHashSet<>(all).size(),
                "pages must not repeat a conversation: " + all);
        assertTrue(all.contains("PATIENT:100") && all.contains("PATIENT:101") && all.contains("STAFF:1"),
                "every thread must appear across the pages: " + all);
        assertEquals(firstPage, partners(conversations(doctorToken, 1, 2)),
                "the same page must return the same order on a repeat request");
    }

    @Test
    void backfill_shouldPreserveHistoryAndBeIdempotent() throws Exception {
        long patientId = createPatient();
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        long first = saveHistoryMessage(patientId, "first", base);
        long second = saveHistoryMessage(patientId, "second", base.plusMinutes(10));
        long third = saveHistoryMessage(patientId, "third", base.plusMinutes(20));
        deleteConversationFor(patientId);

        backfill.backfillMissing();

        ChatConversation conversation = conversationRepository
                .findByPair("PATIENT", patientId, "STAFF", 2L)
                .orElseThrow(() -> new AssertionError("history was not backfilled into a conversation"));
        assertEquals("PATIENT", conversation.getPartyAType(), "the canonical pair puts the patient first");
        assertEquals(patientId, conversation.getPartyAId());
        assertEquals("STAFF", conversation.getPartyBType());
        assertEquals(2L, conversation.getPartyBId());
        assertEquals(3, conversation.getMessageCount(), "all three historical messages count");
        assertEquals(3L, conversation.getRevision());
        assertEquals(third, conversation.getLastMessageId(), "the newest message is the thread's last");
        assertFalse(conversation.getLastMessageTime().isBefore(base.plusMinutes(20)));

        // Ownership survives: the same three rows, still sent by the doctor.
        assertEquals(3, messageRepository.countBetween(2L, "STAFF", patientId, "PATIENT"));
        for (Long id : List.of(first, second, third)) {
            Message reloaded = messageRepository.findById(id).orElseThrow();
            assertEquals("STAFF", reloaded.getSenderType());
            assertEquals(2L, reloaded.getSenderId());
        }

        // A second pass adds nothing: backfill only fills pairs with no row.
        long conversationsBefore = conversationRepository.count();
        assertEquals(0, backfill.backfillMissing());
        assertEquals(conversationsBefore, conversationRepository.count());
        assertEquals(3, conversationRepository.findByPair("PATIENT", patientId, "STAFF", 2L)
                .orElseThrow().getMessageCount(), "a repeat run must not double the count");
    }

    @Test
    void schemaScript_shouldServeACleanInstallAndUpgradeAnOlderDatabase() throws Exception {
        // Clean install: the reviewed schema on an empty database.
        JdbcTemplate clean = probeDatabase("clean");
        runSchemaScript(clean);
        assertTrue(tableExists(clean, "CHAT_CONVERSATION"), "a fresh database must get the table");
        insertConversation(clean, "PATIENT", 103, "STAFF", 103);
        assertThrows(DataIntegrityViolationException.class,
                () -> insertConversation(clean, "PATIENT", 103, "STAFF", 103),
                "the canonical pair is a constraint, not a convention");

        // Upgrade: a database as Round 54.1 left it — messages, no conversation table.
        JdbcTemplate old = probeDatabase("upgrade");
        old.execute(V3_MESSAGE_TABLE);
        insertHistoryMessage(old, 103, 2, "old one", LocalDateTime.now().minusHours(2));
        insertHistoryMessage(old, 103, 2, "old two", LocalDateTime.now().minusHours(1));
        insertHistoryMessage(old, 103, 2, "old three", LocalDateTime.now().minusMinutes(30));
        assertFalse(tableExists(old, "CHAT_CONVERSATION"));

        runSchemaScript(old);
        assertTrue(tableExists(old, "CHAT_CONVERSATION"), "the schema step must add the missing table");

        assertEquals(1, new ChatConversationBackfill(old).backfillMissing());
        Map<String, Object> row = old.queryForMap(
                "SELECT party_a_type, party_a_id, party_b_type, party_b_id, message_count, revision "
                        + "FROM chat_conversation");
        assertEquals("PATIENT", row.get("party_a_type"));
        assertEquals(103L, ((Number) row.get("party_a_id")).longValue());
        assertEquals("STAFF", row.get("party_b_type"));
        assertEquals(2L, ((Number) row.get("party_b_id")).longValue());
        assertEquals(3, ((Number) row.get("message_count")).intValue());
        assertEquals(3L, ((Number) row.get("revision")).longValue());
        assertEquals(3, old.queryForObject("SELECT COUNT(*) FROM message", Integer.class),
                "the upgrade must not consume the message history");
    }

    @Test
    void equalLastMessageTimes_shouldOrderByIdAndKeepThatOrder() throws Exception {
        // Two threads stamped with the same last-message time: the conversation id
        // decides, so the order is determined rather than left to the database. The
        // threads are test-owned, so nothing seeded is disturbed.
        long first = createPatient();
        long second = createPatient();
        send(doctorToken, first, "PATIENT", "tie fixture one");
        send(doctorToken, second, "PATIENT", "tie fixture two");
        jdbcTemplate.update("UPDATE chat_conversation SET last_message_time = ? WHERE "
                        + "(party_a_type = 'PATIENT' AND party_a_id IN (?,?)) "
                        + "OR (party_b_type = 'PATIENT' AND party_b_id IN (?,?))",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(3)),
                first, second, first, second);

        long firstId = conversationIdFor(first);
        long secondId = conversationIdFor(second);
        List<String> all = partners(conversations(doctorToken, 1, 100));
        int firstIndex = all.indexOf("PATIENT:" + first);
        int secondIndex = all.indexOf("PATIENT:" + second);

        assertTrue(firstIndex >= 0 && secondIndex >= 0, "both fixtures must be listed: " + all);
        assertEquals(1, countPartner(conversations(doctorToken, 1, 100), "PATIENT", first));
        assertEquals(firstId > secondId, firstIndex < secondIndex,
                "equal last-message times must fall back to id DESC");
        assertEquals(all, partners(conversations(doctorToken, 1, 100)),
                "the same request must return the same order");
        assertEquals(List.of(all.get(firstIndex)),
                partners(conversations(doctorToken, firstIndex + 1, 1)),
                "a one-row page must take the row at that position, not repeat another");
    }

    // ── helpers ─────────────────────────────────────────────────────

    private long send(String token, long receiverId, String receiverType, String content) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverId", receiverId);
        body.put("receiverType", receiverType);
        body.put("content", content);
        MvcResult result = mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private JsonNode conversations(String token, int page, int size) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/messages/conversations")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");
    }

    private List<String> partners(JsonNode records) {
        List<String> keys = new ArrayList<>();
        for (JsonNode r : records) {
            keys.add(r.get("partnerType").asText() + ":" + r.get("partnerId").asLong());
        }
        return keys;
    }

    private boolean hasPartner(JsonNode records, String type, long id) {
        return countPartner(records, type, id) > 0;
    }

    private int countPartner(JsonNode records, String type, long id) {
        int found = 0;
        for (JsonNode r : records) {
            if (type.equals(r.get("partnerType").asText()) && r.get("partnerId").asLong() == id) found++;
        }
        return found;
    }

    private long createPatient() throws Exception {
        String mrn = "MRN-TEST-CONV-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Test Thread", "mrn", mrn)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        MvcResult list = mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", mrn)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        long patientId = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records").get(0).get("id").asLong();
        createdPatientIds.add(patientId);
        // An appointment puts the patient inside doctor1's clinical scope, which is
        // what permits the send fixtures below.
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", patientId, "doctorId", 2,
                                // A distinct slot per fixture: the 30-minute conflict
                                // check rejects two patients booked at one time.
                                "appointmentTime", LocalDateTime.now().plusDays(2)
                                        .plusMinutes(createdPatientIds.size() * 60L)
                                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "department", "Family Medicine")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        // Delivery needs a portal mailbox behind the record (Round 54.1 review).
        jdbcTemplate.update("INSERT INTO patient_auth (patient_id, username, password, status, failed_attempts,"
                        + " is_deleted, version, create_time, update_time) VALUES (?,?,?,1,0,0,0,NOW(),NOW())",
                patientId, "conv-probe-patient-" + patientId, passwordEncoder.encode("probe123"));
        return patientId;
    }

    /** A historical message written past the send path, i.e. what the backfill exists for. */
    private long saveHistoryMessage(long patientId, String content, LocalDateTime sentAt) {
        Message message = new Message();
        message.setSenderId(2L);
        message.setSenderType("STAFF");
        message.setReceiverId(patientId);
        message.setReceiverType("PATIENT");
        message.setContent(content);
        message.setIsRead(0);
        message.setCreateTime(sentAt);
        message.setUpdateTime(sentAt);
        return messageRepository.save(message).getId();
    }

    private void deleteConversationFor(long patientId) {
        jdbcTemplate.update("DELETE FROM chat_conversation WHERE (party_a_type = 'PATIENT' AND party_a_id = ?) "
                + "OR (party_b_type = 'PATIENT' AND party_b_id = ?)", patientId, patientId);
        Optional<ChatConversation> leftover = conversationRepository
                .findByPair("PATIENT", patientId, "STAFF", 2L);
        assertTrue(leftover.isEmpty(), "the fixture must start from history with no conversation row");
    }

    private JdbcTemplate probeDatabase(String purpose) {
        String url = "jdbc:h2:mem:54_2_" + purpose + "_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
    }

    private void runSchemaScript(JdbcTemplate probe) throws Exception {
        try (Connection connection = probe.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("sql/schema.sql"));
        }
    }

    private boolean tableExists(JdbcTemplate probe, String table) {
        Integer found = probe.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", Integer.class, table);
        return found != null && found > 0;
    }

    private void insertConversation(JdbcTemplate probe, String aType, long aId, String bType, long bId) {
        probe.update("INSERT INTO chat_conversation (party_a_type, party_a_id, party_b_type, party_b_id, "
                        + "last_message_time, message_count, revision, create_time, update_time, is_deleted, version) "
                        + "VALUES (?,?,?,?,CURRENT_TIMESTAMP,1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0)",
                aType, aId, bType, bId);
    }

    private void insertHistoryMessage(JdbcTemplate probe, long patientId, long staffId, String content,
                                      LocalDateTime sentAt) {
        probe.update("INSERT INTO message (sender_id, sender_type, receiver_id, receiver_type, content, "
                        + "is_read, create_time, update_time, is_deleted, version) VALUES (?,?,?,?,?,0,?,?,0,0)",
                staffId, "STAFF", patientId, "PATIENT", content,
                java.sql.Timestamp.valueOf(sentAt), java.sql.Timestamp.valueOf(sentAt));
    }

    private long conversationIdFor(long patientId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM chat_conversation WHERE "
                        + "(party_a_type = 'PATIENT' AND party_a_id = ?) "
                        + "OR (party_b_type = 'PATIENT' AND party_b_id = ?)",
                Long.class, patientId, patientId);
    }
}
