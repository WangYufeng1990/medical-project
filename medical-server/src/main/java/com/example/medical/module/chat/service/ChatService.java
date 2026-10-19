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
import org.springframework.context.ApplicationEventPublisher;
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
    private final StaffAccountState staffAccountState;
    private final PatientPortalAccountState patientPortalAccountState;
    private final DoctorPatientScope doctorPatientScope;
    private final ApplicationEventPublisher eventPublisher;

    public int unreadCount(Long userId, String userType) {
        return messageRepository.countUnreadByUser(userId, userType);
    }

    /**
     * Sends a message from the identity in the security context — the sender is
     * never an argument, so a caller cannot name someone else (Round 54.1).
     *
     * @throws BusinessException 400 for an unusable receiver, 401 without a
     *                           verified sender, 403 for a receiver the sender is
     *                           not entitled to reach, 404 for one that does not
     *                           exist or is not active
     */
    @Transactional
    @Auditable(module = "chat", action = "SEND_MESSAGE", phiAccess = true)
    public MessageVO sendMessage(Long receiverId, String receiverType, String content) {
        Party sender = currentParty();
        Party receiver = new Party(receiverType, receiverId);
        requirePermittedConversation(sender, receiver);

        Message msg = new Message();
        msg.setSenderId(sender.id());
        msg.setSenderType(sender.type());
        msg.setReceiverId(receiver.id());
        msg.setReceiverType(receiver.type());
        msg.setContent(content);
        msg.setIsRead(0);
        messageRepository.save(msg);
        LocalDateTime sentAt = msg.getCreateTime() != null ? msg.getCreateTime() : LocalDateTime.now();
        chatConversationService.recordMessage(sender, receiver, msg.getId(), sentAt);
        MessageVO vo = MessageVO.fromEntity(msg);
        eventPublisher.publishEvent(new NewMessageEvent(sender.id(), sender.type(),
                receiver.id(), receiver.type(), vo));
        return vo;
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
