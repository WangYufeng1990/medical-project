package com.example.medical.module.chat.service;

import com.example.medical.common.result.PageResult;
import com.example.medical.module.chat.dto.ConversationVO;
import com.example.medical.module.chat.dto.MessageVO;
import com.example.medical.module.chat.entity.Message;
import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PatientRepository patientRepository;
    private final SysUserRepository sysUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    public int unreadCount(Long userId, String userType) {
        return messageRepository.countUnreadByUser(userId, userType);
    }

    @Transactional
    @com.example.medical.common.audit.Auditable(module = "chat", action = "SEND_MESSAGE", phiAccess = true)
    public MessageVO sendMessage(Long senderId, String senderType,
                                 Long receiverId, String receiverType, String content) {
        Message msg = new Message();
        msg.setSenderId(senderId);
        msg.setSenderType(senderType);
        msg.setReceiverId(receiverId);
        msg.setReceiverType(receiverType);
        msg.setContent(content);
        msg.setIsRead(0);
        messageRepository.save(msg);
        MessageVO vo = MessageVO.fromEntity(msg);
        eventPublisher.publishEvent(new NewMessageEvent(senderId, senderType,
                receiverId, receiverType, vo));
        return vo;
    }

    @Transactional
    public PageResult<MessageVO> getConversation(Long currentUserId, String currentUserType,
                                                 Long partnerId, String partnerType,
                                                 long page, long size) {
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
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
        List<Message> allMessages = messageRepository.findRecentMessagesByUser(
                currentUserId, currentUserType, PageRequest.of(0, 20));
        if (allMessages.isEmpty()) {
            return PageResult.of(0, size, page, List.of());
        }

        Map<Party, List<Message>> grouped = allMessages.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getSenderId().equals(currentUserId)
                                ? new Party(m.getReceiverType(), m.getReceiverId())
                                : new Party(m.getSenderType(), m.getSenderId()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ConversationVO> conversations = new ArrayList<>();
        for (Map.Entry<Party, List<Message>> entry : grouped.entrySet()) {
            Party partner = entry.getKey();
            List<Message> msgs = entry.getValue();
            Message lastMsg = msgs.get(0);
            // Use countUnread query for accurate count (not limited to 20 latest)
            int unread = messageRepository.countUnread(
                    currentUserId, currentUserType, partner.id(), partner.type());

            conversations.add(ConversationVO.of(partner.id(), partner.type(),
                    resolveName(partner.type(), partner.id()), lastMsg.getContent(),
                    lastMsg.getCreateTime(), unread));
        }

        long offset = (page - 1) * size;
        long total = conversations.size();
        List<ConversationVO> paged = conversations.stream()
                .skip(offset).limit(size).toList();
        return PageResult.of(total, size, page, paged);
    }

    private String resolveName(String type, Long userId) {
        if (STAFF.equals(type)) {
            return sysUserRepository.findById(userId)
                    .map(SysUser::getRealName).orElse("Unknown");
        }
        return patientRepository.findById(userId)
                .map(Patient::getName).orElse("Unknown");
    }

    private record Party(String type, Long id) {}
}
