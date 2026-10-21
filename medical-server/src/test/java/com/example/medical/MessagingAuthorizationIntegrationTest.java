package com.example.medical;

import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.chat.service.ChatConversationBackfill;
import com.example.medical.module.chat.service.ChatService;
import com.example.medical.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 54.1 — identity and recipients.
 * <p>
 * The behaviours this class holds down, each of which the source had wrong:
 * <ul>
 *   <li>{@code DoctorPatientScope} read a missing or unexpected principal as "no
 *       filter", i.e. as ADMIN — so any identity the security context could not
 *       name was granted every patient.</li>
 *   <li>{@code ChatService.sendMessage} persisted whatever receiver id it was
 *       handed, and the sender came from controller arguments rather than from
 *       the verified context.</li>
 *   <li>{@code ChatService.getConversations} decided "my message" by comparing a
 *       bare id, so patient 103 and staff 103 — independent id spaces — were each
 *       shown a conversation with themselves.</li>
 * </ul>
 * Patient 103 (patient4) and probe staff 103 are deliberately chosen so the two
 * identity domains collide on the same number without disturbing the seeded
 * patient1 conversations the older chat tests assert on.
 * <p>
 * <b>Isolation.</b> Every message this class creates is remembered by id and
 * deleted in {@link #cleanUpFixtures()}, and the colliding-thread tests empty that
 * one typed pair before they seed it, so the {@code unreadCount} and
 * "nothing was persisted" assertions hold in any method order. Cleanup never
 * deletes by a bare numeric id: patient 103 and staff 103 both answer to "103",
 * which is exactly the confusion this class is about.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessagingAuthorizationIntegrationTest extends IntegrationTestSupport {

    /** Enabled DOCTOR account whose id equals patient 103's id, with no care relationship. */
    private static final long PROBE_STAFF_ID = 103L;
    /** Disabled DOCTOR account — a receiver that must never be accepted. */
    private static final long DISABLED_STAFF_ID = 104L;
    /** patient4 — the patient whose id collides with PROBE_STAFF_ID. */
    private static final long COLLIDING_PATIENT_ID = 103L;
    private static final String PROBE_STAFF_USERNAME = "msg-probe-staff";
    private static final String DISABLED_STAFF_USERNAME = "msg-probe-disabled";
    private static final String PROBE_PASSWORD = "probe123";

    @Autowired
    private DoctorPatientScope doctorPatientScope;

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatConversationBackfill chatConversationBackfill;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    /** Every NewMessageEvent published while the current test runs. */
    private final List<NewMessageEvent> publishedMessages = new CopyOnWriteArrayList<>();
    /** Ids of the messages this class created, deleted after each test. */
    private final List<Long> createdMessageIds = new CopyOnWriteArrayList<>();
    /** Patients this class registered, deleted with their appointments and portal rows. */
    private final List<Long> createdPatientIds = new CopyOnWriteArrayList<>();

    private ApplicationListener<ApplicationEvent> messageListener;

    private String adminToken;
    private String doctorToken;
    private String patientToken;
    private String collidingPatientToken;
    private String probeStaffToken;

    @BeforeAll
    void setUp() throws Exception {
        adminToken = login("admin", "admin123");
        doctorToken = login("doctor1", "doctor123");
        patientToken = patientLogin("patient1", "patient123");
        collidingPatientToken = patientLogin("patient4", "patient123");
        insertProbeStaff(PROBE_STAFF_ID, PROBE_STAFF_USERNAME, "Probe Staff", 1);
        insertProbeStaff(DISABLED_STAFF_ID, DISABLED_STAFF_USERNAME, "Disabled Probe", 0);
        probeStaffToken = login(PROBE_STAFF_USERNAME, PROBE_PASSWORD);

        // NewMessageEvent is a plain record, so Spring delivers it as a
        // PayloadApplicationEvent payload — a listener typed to the record itself
        // does not satisfy ApplicationListener's bound.
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
        deleteMessagesForPair(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT);
        deleteConversationForPair(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?,?)", PROBE_STAFF_ID, DISABLED_STAFF_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?,?)", PROBE_STAFF_ID, DISABLED_STAFF_ID);
    }

    @BeforeEach
    void resetRecorder() {
        publishedMessages.clear();
        createdMessageIds.clear();
        createdPatientIds.clear();
    }

    @AfterEach
    void cleanUpFixtures() {
        for (Long messageId : createdMessageIds) {
            jdbcTemplate.update("DELETE FROM chat_conversation WHERE last_message_id = ?", messageId);
            jdbcTemplate.update("DELETE FROM message WHERE id = ?", messageId);
        }
        createdMessageIds.clear();
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient_auth WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        createdPatientIds.clear();
        SecurityContextHolder.clearContext();
    }

    // ── A. Patient scope fails closed ────────────────────────────────

    @Test
    void noSecurityContext_shouldNotResolveToUnboundedScope() {
        SecurityContextHolder.clearContext();

        BusinessException resolveFailure = assertThrows(BusinessException.class,
                () -> doctorPatientScope.resolve(),
                "missing authentication must not mean 'no filter'");
        assertEquals(401, resolveFailure.getCode());

        BusinessException accessFailure = assertThrows(BusinessException.class,
                () -> doctorPatientScope.requireAccess(102L),
                "missing authentication must not grant access to an arbitrary patient");
        assertEquals(401, accessFailure.getCode());
    }

    @Test
    void unverifiedPrincipal_shouldNotResolveToUnboundedScope() {
        // The form anonymous/proxied requests take: authenticated as *something*,
        // but not a principal this application issued.
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "anonymousUser", null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        BusinessException failure = assertThrows(BusinessException.class, () -> doctorPatientScope.resolve());
        assertEquals(401, failure.getCode());
    }

    @Test
    void patientPrincipal_shouldNotInheritTheDoctorWithTheSameId() {
        // Patient-token identity 2: sys_user 2 is doctor1, who has patients 100 and
        // 101. Resolving a PATIENT principal through the doctor providers handed
        // that patient another person's patient list.
        LoginUser patient = new LoginUser(2L, "patient-with-doctor-id", "", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                patient, null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));

        BusinessException failure = assertThrows(BusinessException.class, () -> doctorPatientScope.resolve());
        assertEquals(403, failure.getCode());
    }

    @Test
    void adminPrincipal_shouldStayUnscoped() {
        authenticate(1L, "admin", "ROLE_ADMIN");

        assertNull(doctorPatientScope.resolve(), "ADMIN is the only principal without a patient filter");
        assertDoesNotThrow(() -> doctorPatientScope.requireAccess(102L));
    }

    @Test
    void doctorPrincipal_shouldStayScopedToTheirOwnPatients() {
        authenticate(2L, "doctor1", "ROLE_DOCTOR");

        Set<Long> scope = doctorPatientScope.resolve();
        assertNotNull(scope, "a doctor must never resolve to 'no filter'");
        assertTrue(scope.contains(100L), "doctor1 has appointments with patient 100");
        assertFalse(scope.contains(102L), "doctor1 has no relationship with patient 102");

        assertThrows(BusinessException.class, () -> doctorPatientScope.requireAccess(102L));
        assertDoesNotThrow(() -> doctorPatientScope.requireAccess(100L));
    }

    @Test
    void authenticatedStaffReads_shouldKeepWorking() throws Exception {
        mockMvc.perform(get("/api/v1/patients/102/vitals").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/patients/102/vitals").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/100/vitals").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk());
    }

    @Test
    void emergencyAccess_shouldKeepBypassingTheDoctorScope() throws Exception {
        MvcResult em = mockMvc.perform(post("/api/v1/emergency/access/102")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "54.1 break-glass")))
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andReturn();
        String emToken = objectMapper.readTree(em.getResponse().getContentAsString())
                .get("data").get("token").asText();

        mockMvc.perform(get("/api/v1/patients/102/vitals").header("Authorization", "Bearer " + emToken))
                .andExpect(status().isOk());

        // The emergency session is still a DOCTOR identity: its scope is the
        // break-glass patient plus its own relationships, never "no filter".
        SecurityContextHolder.clearContext();
        LoginUser emergency = new LoginUser(2L, "doctor1", "", List.of("EMERGENCY"), 102L, "EMERGENCY");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                emergency, null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
        Set<Long> scope = doctorPatientScope.resolve();
        assertNotNull(scope);
        assertTrue(scope.contains(102L));
    }

    // ── B. Recipients and communication permission ──────────────────

    @Test
    void staffToPatient_withinScope_shouldStillSend() throws Exception {
        long before = countForPair(2L, ChatService.STAFF, 100L, ChatService.PATIENT);

        MvcResult result = sendAsStaff(doctorToken, 100L, "PATIENT", "54.1 in-scope follow-up")
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        createdMessageIds.add(data.get("id").asLong());
        assertEquals("PATIENT", data.get("receiverType").asText());
        assertEquals(100, data.get("receiverId").asLong());
        assertEquals("STAFF", data.get("senderType").asText());
        assertEquals(2, data.get("senderId").asLong(), "sender identity comes from the verified context");
        assertEquals(before + 1, countForPair(2L, ChatService.STAFF, 100L, ChatService.PATIENT));
        assertEquals(1, publishedMessages.size(), "an accepted send publishes exactly one event");
        assertEquals(100L, publishedMessages.get(0).receiverId());
    }

    @Test
    void adminToPatientWithoutCareRelationship_shouldStillSend() throws Exception {
        // ADMIN is unscoped clinically, so no appointment or thread is needed —
        // the deliverability check still applies, and patient 103 has a portal
        // account. This is the pre-54.1 admin flow that must not regress.
        MvcResult result = sendAsStaff(adminToken, COLLIDING_PATIENT_ID, "PATIENT", "54.1 admin outreach")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong());
    }

    @Test
    void staffToPatient_outsideScope_shouldRejectAndPersistNothing() throws Exception {
        long before = countForPair(2L, ChatService.STAFF, 102L, ChatService.PATIENT);

        sendAsStaff(doctorToken, 102L, "PATIENT", "54.1 out-of-scope")
                .andExpect(status().isForbidden());

        assertEquals(before, countForPair(2L, ChatService.STAFF, 102L, ChatService.PATIENT),
                "a rejected send must not leave a message row");
        assertTrue(publishedMessages.isEmpty(), "a rejected send must not publish NewMessageEvent");
    }

    @Test
    void staffToPatient_missingPatient_shouldRejectAndPersistNothing() throws Exception {
        long before = messageRepository.count();

        sendAsStaff(doctorToken, 999_999L, "PATIENT", "54.1 to nobody")
                .andExpect(status().isNotFound());

        assertEquals(before, messageRepository.count());
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void staffToPatient_deletedPatient_shouldRejectAndPersistNothing() throws Exception {
        long patientId = createPatientWithAppointment();
        attachPortalAccount(patientId, 1, false);
        long before = countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT);

        mockMvc.perform(delete("/api/v1/patients/" + patientId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 to a deleted patient")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT),
                "a deleted patient is not a recipient");
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void staffToStaff_shouldStillSend() throws Exception {
        // Staff-to-staff messaging is an existing flow (the chat view links to a
        // colleague with partnerType=STAFF); 54.1 must not close it.
        MvcResult result = sendAsStaff(doctorToken, 1L, "STAFF", "54.1 colleague note")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong());
    }

    @Test
    void staffToStaff_disabledAccount_shouldRejectAndPersistNothing() throws Exception {
        long before = countForPair(2L, ChatService.STAFF, DISABLED_STAFF_ID, ChatService.STAFF);

        sendAsStaff(doctorToken, DISABLED_STAFF_ID, "STAFF", "54.1 to a disabled colleague")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(2L, ChatService.STAFF, DISABLED_STAFF_ID, ChatService.STAFF));
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void patientToRelatedDoctor_shouldStillSend() throws Exception {
        MvcResult result = sendAsPatient(patientToken, 2L, "54.1 reply to my doctor")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senderType").value("PATIENT"))
                .andExpect(jsonPath("$.data.senderId").value(100))
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong());
    }

    @Test
    void patientToUnrelatedStaff_shouldRejectAndPersistNothing() throws Exception {
        long before = countForPair(100L, ChatService.PATIENT, PROBE_STAFF_ID, ChatService.STAFF);

        sendAsPatient(patientToken, PROBE_STAFF_ID, "54.1 to a stranger")
                .andExpect(status().isForbidden());

        assertEquals(before, countForPair(100L, ChatService.PATIENT, PROBE_STAFF_ID, ChatService.STAFF),
                "no care relationship and no existing thread means no authorization");
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void patientToDisabledStaff_shouldRejectAndPersistNothing() throws Exception {
        long before = countForPair(100L, ChatService.PATIENT, DISABLED_STAFF_ID, ChatService.STAFF);

        sendAsPatient(patientToken, DISABLED_STAFF_ID, "54.1 to a disabled account")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(100L, ChatService.PATIENT, DISABLED_STAFF_ID, ChatService.STAFF));
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void patientToMissingStaff_shouldRejectAndPersistNothing() throws Exception {
        long before = messageRepository.count();

        sendAsPatient(patientToken, 999_999L, "54.1 to nobody")
                .andExpect(status().isNotFound());

        assertEquals(before, messageRepository.count());
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void patientToAnotherPatient_shouldRejectAtTheServiceBoundary() {
        // The patient controller pins receiverType to STAFF, so the only way to
        // attempt this is the service boundary the controller is not part of —
        // which is where the check has to live.
        SecurityContextHolder.clearContext();
        LoginUser patient = new LoginUser(100L, "patient1", "", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                patient, null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
        long before = countForPair(100L, ChatService.PATIENT, 101L, ChatService.PATIENT);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> chatService.sendMessage(101L, "PATIENT", "54.1 patient to patient", null));
        assertEquals(403, failure.getCode());
        assertEquals(before, countForPair(100L, ChatService.PATIENT, 101L, ChatService.PATIENT));
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void sendWithoutVerifiedIdentity_shouldRejectAtTheServiceBoundary() {
        SecurityContextHolder.clearContext();
        long before = messageRepository.count();

        BusinessException failure = assertThrows(BusinessException.class,
                () -> chatService.sendMessage(100L, "PATIENT", "54.1 unauthenticated", null));
        assertEquals(401, failure.getCode());
        assertEquals(before, messageRepository.count());
        assertTrue(publishedMessages.isEmpty());
    }

    // ── C. The receiver's portal account state ──────────────────────

    @Test
    void staffToPatient_enabledPortalAccount_shouldSend() throws Exception {
        long patientId = createPatientWithAppointment();
        attachPortalAccount(patientId, 1, false);

        MvcResult result = sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 portal delivery")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong());
        assertEquals(1, publishedMessages.size());
    }

    @Test
    void staffToPatient_disabledPortalAccount_shouldRejectAndPersistNothing() throws Exception {
        long patientId = createPatientWithAppointment();
        attachPortalAccount(patientId, 0, false);
        long before = countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT);

        sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 to a disabled portal account")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT),
                "a disabled portal account cannot be recorded as a delivered message");
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void staffToPatient_softDeletedPortalAccount_shouldRejectAndPersistNothing() throws Exception {
        long patientId = createPatientWithAppointment();
        attachPortalAccount(patientId, 1, false);
        jdbcTemplate.update("UPDATE patient_auth SET is_deleted = 1 WHERE patient_id = ?", patientId);
        long before = countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT);

        sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 to a deleted portal account")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT));
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void staffToPatient_missingPortalAccount_shouldRejectAndPersistNothing() throws Exception {
        long patientId = createPatientWithAppointment();
        long before = countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT);

        sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 to a record with no portal account")
                .andExpect(status().isNotFound());

        assertEquals(before, countForPair(2L, ChatService.STAFF, patientId, ChatService.PATIENT),
                "a patient record without a portal account has nowhere to receive a message");
        assertTrue(publishedMessages.isEmpty());
    }

    @Test
    void staffToPatient_lockedPortalAccount_shouldStillReceive() throws Exception {
        // Lockout is a temporary login state, not a disabled account: the mailbox
        // is still the patient's, so delivery must not be refused (Round 54.1).
        long patientId = createPatientWithAppointment();
        attachPortalAccount(patientId, 1, true);

        MvcResult result = sendAsStaff(doctorToken, patientId, "PATIENT", "54.1 while locked out")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong());
    }

    // ── D. An existing conversation authorizes both directions ──────

    @Test
    void existingConversation_withoutCareRelationship_shouldLetBothSidesReply() throws Exception {
        // Probe staff 103 is a DOCTOR with no appointment or prescription for
        // patient 103: a thread that outlived the care relationship. Before the
        // 54.1 fix the patient could reply and the doctor could not.
        seedExistingThread("staff to patient 103");

        MvcResult fromStaff = sendAsStaff(probeStaffToken, COLLIDING_PATIENT_ID, "PATIENT",
                        "doctor replies in an existing thread")
                .andExpect(status().isOk())
                .andReturn();
        long staffMessageId = objectMapper.readTree(fromStaff.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        createdMessageIds.add(staffMessageId);
        assertEquals(1, publishedMessages.size());
        assertEquals(staffMessageId, publishedMessages.get(0).message().getId());

        MvcResult fromPatient = sendAsPatient(collidingPatientToken, PROBE_STAFF_ID,
                        "patient replies in an existing thread")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(fromPatient.getResponse().getContentAsString())
                .get("data").get("id").asLong());

        assertEquals(3, countForPair(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT));
        assertEquals(2, publishedMessages.size());
    }

    @Test
    void existingConversation_shouldNotWidenClinicalRecordAccess() throws Exception {
        seedExistingThread("staff to patient 103");
        MvcResult fromPatient = sendAsPatient(collidingPatientToken, PROBE_STAFF_ID,
                        "patient message in an existing thread")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(fromPatient.getResponse().getContentAsString())
                .get("data").get("id").asLong());
        MvcResult fromStaff = sendAsStaff(probeStaffToken, COLLIDING_PATIENT_ID, "PATIENT",
                        "doctor message in an existing thread")
                .andExpect(status().isOk())
                .andReturn();
        createdMessageIds.add(objectMapper.readTree(fromStaff.getResponse().getContentAsString())
                .get("data").get("id").asLong());

        // Messaging permission is not record permission: the same doctor still
        // cannot read one byte of patient 103's clinical record.
        mockMvc.perform(get("/api/v1/patients/103/vitals")
                        .header("Authorization", "Bearer " + probeStaffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/patients/103")
                        .header("Authorization", "Bearer " + probeStaffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/fhir/Patient/103")
                        .header("Authorization", "Bearer " + probeStaffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void noRelationshipAndNoConversation_shouldRejectBothDirections() throws Exception {
        // doctor1 (id 2) has no appointment or prescription for patient 103, and
        // this test creates no thread for the pair.
        long before = countForPair(2L, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT);

        sendAsStaff(doctorToken, COLLIDING_PATIENT_ID, "PATIENT", "54.1 unrelated doctor")
                .andExpect(status().isForbidden());
        sendAsPatient(collidingPatientToken, 2L, "54.1 unrelated doctor, from the patient")
                .andExpect(status().isForbidden());

        assertEquals(before, countForPair(2L, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT));
        assertTrue(publishedMessages.isEmpty());
    }

    // ── E. Same numeric id, different party ─────────────────────────

    @Test
    void patientConversations_shouldNotInventAConversationWithItself() throws Exception {
        seedCollidingThread();

        JsonNode records = conversations("/api/v1/patient/me/messages/conversations", collidingPatientToken);

        for (JsonNode r : records) {
            assertFalse(r.get("partnerType").asText().equals("PATIENT") && r.get("partnerId").asLong() == 103L,
                    "patient 103 was shown a conversation with patient 103 — the staff 103 messages were "
                            + "read as its own");
        }
        JsonNode staffThread = findConversation(records, "STAFF", 103L);
        assertNotNull(staffThread, "the staff 103 thread is the patient's only counterpart");
        assertEquals("Probe Staff", staffThread.get("partnerName").asText());
        assertEquals("colliding patient to staff", staffThread.get("lastMessage").asText());
        assertEquals(1, staffThread.get("unreadCount").asInt());
    }

    @Test
    void staffConversations_shouldNotInventAConversationWithItself() throws Exception {
        seedCollidingThread();

        JsonNode records = conversations("/api/v1/messages/conversations", probeStaffToken);

        for (JsonNode r : records) {
            assertFalse(r.get("partnerType").asText().equals("STAFF") && r.get("partnerId").asLong() == 103L,
                    "staff 103 was shown a conversation with staff 103 — the patient 103 messages were "
                            + "read as its own");
        }
        JsonNode patientThread = findConversation(records, "PATIENT", 103L);
        assertNotNull(patientThread);
        assertEquals("colliding patient to staff", patientThread.get("lastMessage").asText());
        assertEquals(1, patientThread.get("unreadCount").asInt());
    }

    @Test
    void collidingThreadMessages_shouldKeepTheirTypedOwnership() throws Exception {
        seedCollidingThread();

        MvcResult result = mockMvc.perform(get("/api/v1/patient/me/messages/103")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + collidingPatientToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");

        JsonNode fromStaff = null;
        JsonNode fromPatient = null;
        for (JsonNode m : records) {
            if ("colliding staff to patient".equals(m.get("content").asText())) fromStaff = m;
            if ("colliding patient to staff".equals(m.get("content").asText())) fromPatient = m;
        }
        assertNotNull(fromStaff, "the staff message must be in the thread");
        assertNotNull(fromPatient, "the patient message must be in the thread");
        assertEquals("STAFF", fromStaff.get("senderType").asText());
        assertEquals(103, fromStaff.get("senderId").asLong());
        assertEquals("PATIENT", fromPatient.get("senderType").asText());
        assertEquals(103, fromPatient.get("senderId").asLong());
    }

    // ── helpers ────────────────────────────────────────────────────

    private void authenticate(Long userId, String username, String role) {
        LoginUser user = new LoginUser(userId, username, "", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private void insertProbeStaff(long id, String username, String realName, int status) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, real_name, status, is_deleted, version,"
                        + " create_time, update_time) VALUES (?,?,?,?,?,0,0,NOW(),NOW())",
                id, username, passwordEncoder.encode(PROBE_PASSWORD), realName, status);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?)", id, 2L);
    }

    /** A typed count, so patient 103's messages can never be read as staff 103's. */
    private long countForPair(long idA, String typeA, long idB, String typeB) {
        return messageRepository.countBetween(idA, typeA, idB, typeB);
    }

    private void deleteConversationForPair(long idA, String typeA, long idB, String typeB) {
        jdbcTemplate.update("DELETE FROM chat_conversation WHERE (party_a_type = ? AND party_a_id = ? "
                        + "AND party_b_type = ? AND party_b_id = ?) OR (party_a_type = ? AND party_a_id = ? "
                        + "AND party_b_type = ? AND party_b_id = ?)",
                typeA, idA, typeB, idB, typeB, idB, typeA, idA);
    }

    private void deleteMessagesForPair(long idA, String typeA, long idB, String typeB) {
        jdbcTemplate.update("DELETE FROM message WHERE (sender_id = ? AND sender_type = ? AND receiver_id = ? "
                        + "AND receiver_type = ?) OR (sender_id = ? AND sender_type = ? AND receiver_id = ? "
                        + "AND receiver_type = ?)",
                idA, typeA, idB, typeB, idB, typeB, idA, typeA);
    }

    private ResultActions sendAsStaff(String token, long receiverId, String receiverType, String content)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("receiverId", receiverId);
        body.put("receiverType", receiverType);
        body.put("content", content);
        return mockMvc.perform(post("/api/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions sendAsPatient(String token, long receiverId, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/patient/me/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("receiverId", receiverId, "content", content)))
                .header("Authorization", "Bearer " + token));
    }

    private JsonNode conversations(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .param("page", "1").param("size", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("records");
    }

    private JsonNode findConversation(JsonNode records, String partnerType, long partnerId) {
        for (JsonNode r : records) {
            if (partnerType.equals(r.get("partnerType").asText()) && r.get("partnerId").asLong() == partnerId) {
                return r;
            }
        }
        return null;
    }

    /** Empties the one colliding pair this class seeds, then writes both directions. */
    private void seedCollidingThread() {
        resetCollidingThread();
        LocalDateTime base = LocalDateTime.now().minusMinutes(5);
        createdMessageIds.add(saveMessage(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID,
                ChatService.PATIENT, "colliding staff to patient", 0, base));
        createdMessageIds.add(saveMessage(COLLIDING_PATIENT_ID, ChatService.PATIENT, PROBE_STAFF_ID,
                ChatService.STAFF, "colliding patient to staff", 0, base.plusMinutes(1)));
        chatConversationBackfill.backfillMissing();
    }

    /**
     * One pre-existing message from the staff side — the arrangement P1 is about.
     * <p>
     * From Round 54.2 the conversation list is driven by conversation rows, so a
     * fixture that writes raw history also runs the backfill: that is exactly how
     * real history becomes a thread.
     */
    private void seedExistingThread(String content) {
        resetCollidingThread();
        createdMessageIds.add(saveMessage(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID,
                ChatService.PATIENT, content, 0, LocalDateTime.now().minusMinutes(5)));
        chatConversationBackfill.backfillMissing();
    }

    /** The pair starts as history only: no messages, and no conversation row pointing at them. */
    private void resetCollidingThread() {
        deleteConversationForPair(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT);
        deleteMessagesForPair(PROBE_STAFF_ID, ChatService.STAFF, COLLIDING_PATIENT_ID, ChatService.PATIENT);
    }

    private long saveMessage(long senderId, String senderType, long receiverId, String receiverType,
                             String content, int isRead, LocalDateTime createdAt) {
        Message m = new Message();
        m.setSenderId(senderId);
        m.setSenderType(senderType);
        m.setReceiverId(receiverId);
        m.setReceiverType(receiverType);
        m.setContent(content);
        m.setIsRead(isRead);
        m.setCreateTime(createdAt);
        m.setUpdateTime(createdAt);
        return messageRepository.save(m).getId();
    }

    /** Registers a TEST patient and books doctor1 with them, so a scope relationship exists. */
    private long createPatientWithAppointment() throws Exception {
        String mrn = "MRN-TEST-MSGAUTH-" + System.nanoTime();
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Test Recipient", "mrn", mrn)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/v1/patients")
                        .param("keyword", mrn)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("data").get("records");
        assertTrue(records.size() > 0, "created patient must be listable");
        long patientId = records.get(0).get("id").asLong();
        createdPatientIds.add(patientId);
        // The cleanup SQL deletes patients by TEST name/MRN; this list makes the
        // same deletion happen per test, so one test never sees another's patient.
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "patientId", patientId, "doctorId", 2,
                                "appointmentTime", LocalDateTime.now().plusDays(3)
                                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                "department", "Family Medicine")))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return patientId;
    }

    /** The patient's portal credential row: status 0/1 and an optional future lockout. */
    private void attachPortalAccount(long patientId, int status, boolean lockedOut) {
        jdbcTemplate.update("INSERT INTO patient_auth (patient_id, username, password, status, locked_until,"
                        + " failed_attempts, is_deleted, version, create_time, update_time)"
                        + " VALUES (?,?,?,?,?,?,0,0,NOW(),NOW())",
                patientId, "msg-probe-patient-" + patientId, passwordEncoder.encode(PROBE_PASSWORD), status,
                lockedOut ? LocalDateTime.now().plusMinutes(10) : null, lockedOut ? 5 : 0);
    }
}
