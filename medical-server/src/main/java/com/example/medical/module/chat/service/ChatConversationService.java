package com.example.medical.module.chat.service;

import com.example.medical.module.chat.entity.ChatConversation;
import com.example.medical.module.chat.repository.ChatConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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
     * Records one message on the thread (Round 54.2).
     * <p>
     * Two statements, each safe under concurrency and each doing one job: the first
     * creates the thread or counts the message in a single create-or-update, so
     * simultaneous first messages cannot collide on the pair's unique key; the second
     * moves the last-message pointer only forward, ordered by {@code (time, id)}.
     * A message created earlier can be recorded later, and it must not rewind the
     * thread — the failure this pair of statements exists to prevent.
     */
    @Transactional
    public void recordMessage(Party sender, Party receiver, Long messageId, LocalDateTime time) {
        Party first = canonicalFirst(sender, receiver);
        Party second = first.equals(sender) ? receiver : sender;
        LocalDateTime sentAt = storedPrecision(time);

        conversationRepository.recordMessage(first.type(), first.id(), second.type(), second.id(),
                messageId, sentAt);
        conversationRepository.advanceLastMessage(first.type(), first.id(), second.type(), second.id(),
                messageId, sentAt);
    }

    /**
     * The precision the thread's timestamp is stored and compared at. MySQL's
     * {@code TIMESTAMP} holds whole seconds, so the value the application compares
     * must be the same one the database keeps — otherwise two writes of the same
     * message can disagree about which is later. Messages within one second are
     * ordered by their id, which is what the pointer rule already does.
     */
    static LocalDateTime storedPrecision(LocalDateTime time) {
        return time.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * The thread's current revision, for the dispatch row written with the message
     * (Round 55.2). Zero when the thread is somehow absent, which the contract reads as
     * "no revision yet" rather than failing the send.
     */
    public long revisionOf(Party one, Party other) {
        Party first = canonicalFirst(one, other);
        Party second = first.equals(one) ? other : one;
        return conversationRepository.findByPair(first.type(), first.id(), second.type(), second.id())
                .map(conversation -> conversation.getRevision() == null ? 0L : conversation.getRevision())
                .orElse(0L);
    }

    /** One page of the caller's threads, newest activity first with a stable tie-break. */
    public Page<ChatConversation> page(Long userId, String userType, Pageable pageable) {
        return conversationRepository.findPageForParty(userType, userId, pageable);
    }


    private Party canonicalFirst(Party one, Party other) {
        return one.compareTo(other) <= 0 ? one : other;
    }
}
