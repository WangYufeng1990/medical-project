package com.example.medical.module.chat.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.base.Pages;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.lookup.PatientLookup;
import com.example.medical.common.lookup.StaffLookup;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.common.security.PatientPortalAccountState;
import com.example.medical.common.security.StaffAccountState;
import com.example.medical.module.chat.dto.ConversationVO;
import com.example.medical.module.chat.dto.MessageVO;
import com.example.medical.module.chat.entity.ChatConversation;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    // Party types: patient.id and sys_user.id are independent sequences, so a
    // bare Long cannot identify a message party (Post-Round 44 review R2-1).
    public static final String STAFF = "STAFF";
    public static final String PATIENT = "PATIENT";

    private final MessageRepository messageRepository;
    private final PatientLookup patientLookup;
    private final StaffLookup staffLookup;
    private final ChatConversationService chatConversationService;
    private final AgentRunService agentRunService;
    private final StaffAccountState staffAccountState;
    private final PatientPortalAccountState patientPortalAccountState;
    private final DoctorPatientScope doctorPatientScope;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public int unreadCount(Long userId, String userType) {
        return messageRepository.countUnreadByUser(userId, userType);
    }

    /**
     * Sends a message from the identity in the security context — the sender is
     * never an argument, so a caller cannot name someone else (Round 54.1).
     * <p>
     * With a {@code clientMessageId} the send is idempotent (Round 54.3): the same key
     * from the same sender returns the message that already exists, and the same key
     * with a different payload is a conflict. The transaction lives in a
     * {@link TransactionTemplate} rather than an annotation because a concurrent
     * duplicate is rejected by the unique index at flush time: that leaves the first
     * transaction rolled back and unusable, so resolving the duplicate has to happen
     * in a fresh one instead of being swallowed inside a poisoned transaction.
     *
     * @throws BusinessException 400 for an unusable receiver, 401 without a
     *                           verified sender, 403 for a receiver the sender is
     *                           not entitled to reach, 404 for one that does not
     *                           exist or is not active, 409 for a reused key with a
     *                           different payload
     */
    @Auditable(module = "chat", action = "SEND_MESSAGE", phiAccess = true)
    public MessageVO sendMessage(Long receiverId, String receiverType, String content,
                                 String clientMessageId) {
        String key = normalizeKey(clientMessageId);
        try {
            return transactionTemplate.execute(status ->
                    deliver(receiverId, receiverType, content, key));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return transactionTemplate.execute(status ->
                    alreadyStored(receiverId, receiverType, content, key));
        }
    }

    private MessageVO deliver(Long receiverId, String receiverType, String content, String clientMessageId) {
        return deliverAs(currentParty(), receiverId, receiverType, content, clientMessageId);
    }

    /**
     * Delivers a message as an explicit party (Round 57.2: an approved agent draft is published
     * into the thread by the clinician who owns it, while the approving human is recorded in the
     * decision row).
     */
    public MessageVO deliverAs(Party sender, Long receiverId, String receiverType, String content,
                               String clientMessageId) {
        Party receiver = new Party(receiverType, receiverId);
        requirePermittedConversation(sender, receiver);

        if (clientMessageId != null) {
            Optional<Message> stored = messageRepository
                    .findBySenderTypeAndSenderIdAndClientMessageId(sender.type(), sender.id(), clientMessageId);
            if (stored.isPresent()) return sameMessageOrConflict(stored.get(), receiver, content);
        }

        Message msg = new Message();
        msg.setSenderId(sender.id());
        msg.setSenderType(sender.type());
        msg.setReceiverId(receiver.id());
        msg.setReceiverType(receiver.type());
        msg.setContent(content);
        msg.setIsRead(0);
        msg.setClientMessageId(clientMessageId);
        // One clock for the message row and the thread (Round 54.2 review). Normalising
        // before the insert is what makes the stored message time equal to the stored
        // thread time: a database that rounds fractional seconds into the next second
        // could otherwise order the two differently from the application.
        LocalDateTime sentAt = ChatConversationService.storedPrecision(LocalDateTime.now());
        msg.setCreateTime(sentAt);
        msg.setUpdateTime(sentAt);
        // Flushed here, not at commit, so a duplicate key surfaces inside this
        // transaction (and is translated) instead of as an opaque rollback.
        messageRepository.saveAndFlush(msg);
        chatConversationService.recordMessage(sender, receiver, msg.getId(), sentAt);
        // Round 55.2: the job is written in this transaction, so a stored patient message
        // always has one; staff-to-staff and staff-to-patient traffic is not model work.
        if (PATIENT.equals(sender.type())) {
            agentRunService.recordForMessage(msg, sender.id());
        } else if (PATIENT.equals(receiver.type())) {
            // A human answer ends the review: the waiting draft is stale from here on (57.3).
            agentRunService.supersedeWaitingFor(receiver.id());
        }
        MessageVO vo = MessageVO.fromEntity(msg);
        notifyAfterCommit(new NewMessageEvent(sender.id(), sender.type(),
                receiver.id(), receiver.type(), vo));
        return vo;
    }

    /**
     * Delivers the "a message was stored" event once the transaction that stored it has
     * committed (Round 54.4). Publishing inside the transaction let a send that rolled
     * back afterwards still push {@code new_message} to the receiver — a notification
     * for a message nobody could load.
     */
    private void notifyAfterCommit(NewMessageEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishQuietly(event);
                }
            });
        } else {
            publishQuietly(event);
        }
    }

    /**
     * A notification that fails is logged, not propagated: the message is already
     * committed and the caller must not be told the send failed. The receiver's client
     * reconciles from the database, which is the record of truth.
     */
    private void publishQuietly(NewMessageEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("Chat notification failed after commit; the message is stored", e);
        }
    }

    /**
     * The winner of a concurrent duplicate left its message behind while this attempt
     * rolled back; answer with it or report the conflict. The row is read in its own
     * transaction because the failed one is gone.
     */
    private MessageVO alreadyStored(Long receiverId, String receiverType, String content, String clientMessageId) {
        Party sender = currentParty();
        Message stored = messageRepository
                .findBySenderTypeAndSenderIdAndClientMessageId(sender.type(), sender.id(), clientMessageId)
                .orElseThrow(() -> new BusinessException(ResultCode.CONFLICT,
                        "The message could not be stored; retry the send"));
        return sameMessageOrConflict(stored, new Party(receiverType, receiverId), content);
    }

    private MessageVO sameMessageOrConflict(Message stored, Party receiver, String content) {
        boolean samePayload = Objects.equals(stored.getReceiverType(), receiver.type())
                && Objects.equals(stored.getReceiverId(), receiver.id())
                && Objects.equals(stored.getContent(), content);
        if (!samePayload) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "clientMessageId was already used for a different message");
        }
        return MessageVO.fromEntity(stored);
    }

    private String normalizeKey(String clientMessageId) {
        return clientMessageId == null || clientMessageId.isBlank() ? null : clientMessageId;
    }

    @Transactional
    public PageResult<MessageVO> getConversation(Long currentUserId, String currentUserType,
                                                 Long partnerId, String partnerType,
                                                 long page, long size) {
        Pageable pageable = Pages.of(page, size);
        var result = messageRepository.findMessagesBetween(
                currentUserId, currentUserType, partnerId, partnerType, pageable);

        // Use @Modifying UPDATE for reliable persistence
        messageRepository.markAsRead(currentUserId, currentUserType, partnerId, partnerType);

        List<MessageVO> records = result.getContent().stream()
                .map(MessageVO::fromEntity).toList();
        return PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, records);
    }

    public PageResult<ConversationVO> getConversations(Long currentUserId, String currentUserType,
                                                        long page, long size) {
        Page<ChatConversation> found = chatConversationService.page(
                currentUserId, currentUserType, Pages.of(page, size));
        if (found.isEmpty()) {
            return PageResult.of(found.getTotalElements(), found.getSize(), page, List.of());
        }

        Map<Party, Integer> unread = unreadByPartner(currentUserId, currentUserType);
        Map<Long, Message> lastMessages = messageRepository
                .findAllById(found.getContent().stream()
                        .map(ChatConversation::getLastMessageId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(Message::getId, message -> message));

        List<ConversationVO> records = found.getContent().stream().map(conversation -> {
            Party partner = partnerOf(conversation, currentUserId, currentUserType);
            Message last = lastMessages.get(conversation.getLastMessageId());
            return ConversationVO.of(partner.id(), partner.type(),
                    resolveName(partner.type(), partner.id()),
                    last != null ? last.getContent() : "",
                    conversation.getLastMessageTime(),
                    unread.getOrDefault(partner, 0));
        }).toList();
        return PageResult.of(found.getTotalElements(), found.getSize(), found.getNumber() + 1, records);
    }

    /**
     * The other side of a stored conversation. The row holds both parties with their
     * own type, so "which one is not me" is answered by the pair itself — patient 103
     * and staff 103 are different people with the same number (Round 54.1), and a bare
     * id comparison filed the staff's messages as the patient's own.
     */
    private Party partnerOf(ChatConversation conversation, Long currentUserId, String currentUserType) {
        boolean callerIsA = Objects.equals(currentUserType, conversation.getPartyAType())
                && Objects.equals(currentUserId, conversation.getPartyAId());
        return callerIsA
                ? new Party(conversation.getPartyBType(), conversation.getPartyBId())
                : new Party(conversation.getPartyAType(), conversation.getPartyAId());
    }

    private Map<Party, Integer> unreadByPartner(Long receiverId, String receiverType) {
        Map<Party, Integer> counts = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadGroupedBySender(receiverId, receiverType)) {
            counts.put(new Party((String) row[0], ((Number) row[1]).longValue()),
                    ((Number) row[2]).intValue());
        }
        return counts;
    }

    /**
     * The other party of a message. "Mine" is both the type and the id: patient 103
     * and staff 103 are different people, and comparing the bare id filed the
     * staff's messages as the patient's own outgoing ones — a conversation with
     * themselves (Round 54.1).
     */
    private Party partnerOf(Message message, Long currentUserId, String currentUserType) {
        boolean mine = Objects.equals(currentUserId, message.getSenderId())
                && Objects.equals(currentUserType, message.getSenderType());
        return mine
                ? new Party(message.getReceiverType(), message.getReceiverId())
                : new Party(message.getSenderType(), message.getSenderId());
    }

    private Party currentParty() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser user)
                || user.getUserId() == null || user.getUserId() <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "No verified sender identity");
        }
        boolean patient = hasRole(auth, "ROLE_PATIENT");
        boolean staff = hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_DOCTOR");
        if (patient == staff) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Messaging requires exactly one of the staff or patient identities");
        }
        return new Party(patient ? PATIENT : STAFF, user.getUserId());
    }

    private void requirePermittedConversation(Party sender, Party receiver) {
        if (!STAFF.equals(receiver.type()) && !PATIENT.equals(receiver.type())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "receiverType must be STAFF or PATIENT");
        }
        if (receiver.id() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "receiverId is required");
        }
        if (sender.equals(receiver)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot message yourself");
        }

        if (PATIENT.equals(sender.type())) {
            if (!STAFF.equals(receiver.type())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "Patients may only message staff");
            }
            requireActiveStaff(receiver.id());
            if (!established(sender, receiver) && !doctorPatientScope.isRelated(receiver.id(), sender.id())) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "No care relationship or existing conversation with this staff member");
            }
            return;
        }

        if (PATIENT.equals(receiver.type())) {
            requireDeliverablePatient(receiver.id());
            // Clinical scope decides who a clinician may contact, but messaging
            // never feeds back into it: permits() is read here, nothing is merged.
            if (!established(sender, receiver) && !doctorPatientScope.permits(receiver.id())) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "No care relationship or existing conversation with this patient");
            }
            return;
        }

        requireActiveStaff(receiver.id());
    }

    /**
     * The relationship clause is shared by both directions on purpose: an
     * established conversation authorizes a reply from either side. The first
     * 54.1 implementation applied it to the patient only, so a thread that outlived
     * its appointment could be answered one way and refused the other.
     */
    private boolean established(Party sender, Party receiver) {
        return messageRepository.hasConversation(
                sender.id(), sender.type(), receiver.id(), receiver.type());
    }

    /** The record must exist, and there must be a portal account behind it to deliver to. */
    private void requireDeliverablePatient(Long patientId) {
        if (!patientLookup.exists(patientId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Patient not found");
        }
        if (!patientPortalAccountState.isActive(patientId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Patient portal account not found or not active");
        }
    }

    private void requireActiveStaff(Long staffId) {
        if (!staffAccountState.isActive(staffId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Staff account not found or not active");
        }
    }

    private boolean hasRole(Authentication auth, String role) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (role.equals(authority.getAuthority())) return true;
        }
        return false;
    }

    private String resolveName(String type, Long userId) {
        if (STAFF.equals(type)) {
            return staffLookup.realName(userId).orElse("Unknown");
        }
        return patientLookup.displayName(userId).orElse("Unknown");
    }

}
