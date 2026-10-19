package com.example.medical.module.chat.service;

import com.example.medical.module.chat.entity.ChatConversation;
import com.example.medical.module.chat.repository.ChatConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Keeps the conversation row in step with the messages of a thread (Round 54.2).
 * <p>
 * The pair is stored in its canonical {@code (type, id)} order, so the same thread
 * has one row whichever side writes first; the unique key on those four columns is
 * what makes that an invariant rather than a convention.
 */
@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private final ChatConversationRepository conversationRepository;

    /**
     * Records one message on the thread, creating the row for a first message.
     * <p>
     * The existing-thread path is a single incrementing UPDATE, so two senders on
     * the same thread cannot lose each other's revision to a read-modify-write. The
     * first-message path is a plain insert: a simultaneous first message from both
     * sides collides on the unique key and fails loudly instead of storing the same
     * thread twice.
     */
    @Transactional
    public void recordMessage(Party sender, Party receiver, Long messageId, LocalDateTime time) {
        Party first = canonicalFirst(sender, receiver);
        Party second = first.equals(sender) ? receiver : sender;

        Optional<ChatConversation> existing =
                conversationRepository.findByPair(first.type(), first.id(), second.type(), second.id());
        if (existing.isPresent()) {
            conversationRepository.recordMessage(first.type(), first.id(), second.type(), second.id(),
                    messageId, time);
            return;
        }

        ChatConversation conversation = new ChatConversation();
        conversation.setPartyAType(first.type());
        conversation.setPartyAId(first.id());
        conversation.setPartyBType(second.type());
        conversation.setPartyBId(second.id());
        conversation.setLastMessageId(messageId);
        conversation.setLastMessageTime(time);
        conversation.setMessageCount(1);
        conversation.setRevision(1L);
        conversationRepository.save(conversation);
    }

    /** One page of the caller's threads, newest activity first with a stable tie-break. */
    public Page<ChatConversation> page(Long userId, String userType, Pageable pageable) {
        return conversationRepository.findPageForParty(userType, userId, pageable);
    }


    private Party canonicalFirst(Party one, Party other) {
        return one.compareTo(other) <= 0 ? one : other;
    }
}
