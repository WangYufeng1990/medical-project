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

    private final MessageRepository messageRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository sysUserRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public MessageVO sendMessage(Long senderId, Long receiverId, String content) {
        Message msg = new Message();
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setIsRead(0);
        messageRepository.save(msg);
        MessageVO vo = MessageVO.fromEntity(msg);
        eventPublisher.publishEvent(new NewMessageEvent(senderId, receiverId, vo));
        return vo;
    }

    public PageResult<MessageVO> getConversation(Long currentUserId, Long partnerId,
                                                  long page, long size) {
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        var result = messageRepository.findMessagesBetween(
                currentUserId, partnerId, pageable);

        List<Message> toMark = result.getContent().stream()
                .filter(m -> m.getReceiverId().equals(currentUserId) && m.getIsRead() == 0)
                .toList();
        if (!toMark.isEmpty()) {
            for (Message m : toMark) {
                m.setIsRead(1);
            }
            messageRepository.saveAll(toMark);
        }

        List<MessageVO> records = result.getContent().stream()
                .map(MessageVO::fromEntity).toList();
        return PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, records);
    }

    public PageResult<ConversationVO> getConversations(Long currentUserId,
                                                        long page, long size) {
        List<Message> allMessages = messageRepository.findRecentMessagesByUser(currentUserId, 20);
        if (allMessages.isEmpty()) {
            return PageResult.of(0, size, page, List.of());
        }

        Map<Long, List<Message>> grouped = allMessages.stream()
                .collect(Collectors.groupingBy(m ->
                        m.getSenderId().equals(currentUserId) ? m.getReceiverId() : m.getSenderId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<ConversationVO> conversations = new ArrayList<>();
        for (Map.Entry<Long, List<Message>> entry : grouped.entrySet()) {
            Long partnerId = entry.getKey();
            List<Message> msgs = entry.getValue();
            Message lastMsg = msgs.get(0);
            int unread = (int) msgs.stream()
                    .filter(m -> m.getReceiverId().equals(currentUserId) && m.getIsRead() == 0)
                    .count();

            conversations.add(ConversationVO.of(partnerId,
                    resolveName(partnerId), lastMsg.getContent(),
                    lastMsg.getCreateTime(), unread));
        }

        long offset = (page - 1) * size;
        long total = conversations.size();
        List<ConversationVO> paged = conversations.stream()
                .skip(offset).limit(size).toList();
        return PageResult.of(total, size, page, paged);
    }

    private String resolveName(Long userId) {
        Optional<Patient> patient = patientRepository.findById(userId);
        if (patient.isPresent()) return patient.get().getName();
        Optional<SysUser> sysUser = sysUserRepository.findById(userId);
        if (sysUser.isPresent()) return sysUser.get().getRealName();
        return "Unknown";
    }
}
