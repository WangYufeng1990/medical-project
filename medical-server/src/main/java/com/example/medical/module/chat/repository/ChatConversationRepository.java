package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /**
     * Row lock on the thread (Round 57.3 review). Every path that appends to a conversation or
     * publishes into it takes this lock first, which is what gives "check the latest message, then
     * publish" a defined order: a message either commits before the check or waits for it.
     */
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM ChatConversation c WHERE c.id = :id")
    Optional<ChatConversation> findByIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id);

    /**
     * The thread between two parties, whichever order the pair is given in. The
     * canonical pair is the storage invariant; accepting either order here is what
     * lets the send path stay unaware of it.
     */
    @Query("SELECT c FROM ChatConversation c WHERE ("
            + "(c.partyAType = :typeA AND c.partyAId = :idA AND c.partyBType = :typeB AND c.partyBId = :idB)"
            + " OR (c.partyAType = :typeB AND c.partyAId = :idB AND c.partyBType = :typeA AND c.partyBId = :idA))")
    Optional<ChatConversation> findByPair(@Param("typeA") String typeA, @Param("idA") Long idA,
                                          @Param("typeB") String typeB, @Param("idB") Long idB);

    /**
     * One conversation per thread the caller takes part in, newest activity first.
     * {@code id DESC} is the tie-break: two threads with the same last-message
     * timestamp keep one order across requests and pages.
     */
    @Query("SELECT c FROM ChatConversation c WHERE "
            + "(c.partyAType = :type AND c.partyAId = :id) OR (c.partyBType = :type AND c.partyBId = :id) "
            + "ORDER BY c.lastMessageTime DESC, c.id DESC")
    Page<ChatConversation> findPageForParty(@Param("type") String type, @Param("id") Long id,
                                            Pageable pageable);

    /**
     * Every thread that involves one party type (Round 57.1: an ADMIN reviewing all patient
     * threads). Newest activity first, with the same stable tie-break as the per-party listing.
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.partyAType = :type OR c.partyBType = :type "
            + "ORDER BY c.lastMessageTime DESC, c.id DESC")
    Page<ChatConversation> findPageForType(@Param("type") String type, Pageable pageable);

    /**
     * Creates the thread when it is missing, without counting a message (Round 57.3 review). The
     * send path calls this and then locks the row <em>before</em> it creates the message, so a
     * concurrently publishing reply cannot take a later message id and commit first.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO chat_conversation (party_a_type, party_a_id, party_b_type, party_b_id, "
            + "last_message_id, last_message_time, message_count, revision, create_time, update_time, "
            + "is_deleted, version) VALUES (:typeA, :idA, :typeB, :idB, NULL, "
            + "TIMESTAMP '1970-01-01 00:00:00', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0) "
            + "ON DUPLICATE KEY UPDATE is_deleted = 0", nativeQuery = true)
    int ensureThread(@Param("typeA") String typeA, @Param("idA") Long idA,
                     @Param("typeB") String typeB, @Param("idB") Long idB);

    /** The canonical pair's row, locked for the rest of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ChatConversation c WHERE c.partyAType = :typeA AND c.partyAId = :idA "
            + "AND c.partyBType = :typeB AND c.partyBId = :idB")
    Optional<ChatConversation> lockByPair(@Param("typeA") String typeA, @Param("idA") Long idA,
                                          @Param("typeB") String typeB, @Param("idB") Long idB);

    /**
     * Creates the thread when it is missing and counts the message when it is not, in
     * one statement.
     * <p>
     * A find-then-insert let two concurrent first messages both see "no thread" and
     * both insert; the pair's unique key then rejected one of them and its transaction
     * took the already-written message down with it (Round 54.2 review). The insert
     * branch also un-deletes a thread that was previously removed, because a new
     * message means the thread exists again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO chat_conversation (party_a_type, party_a_id, party_b_type, party_b_id, "
            + "last_message_id, last_message_time, message_count, revision, create_time, update_time, "
            + "is_deleted, version) "
            + "VALUES (:typeA, :idA, :typeB, :idB, :messageId, :time, 1, 1, CURRENT_TIMESTAMP, :time, 0, 0) "
            + "ON DUPLICATE KEY UPDATE message_count = message_count + 1, revision = revision + 1, "
            + "is_deleted = 0, update_time = CASE WHEN :time > update_time THEN :time ELSE update_time END",
            nativeQuery = true)
    int recordMessage(@Param("typeA") String typeA, @Param("idA") Long idA,
                      @Param("typeB") String typeB, @Param("idB") Long idB,
                      @Param("messageId") Long messageId, @Param("time") LocalDateTime time);

    /**
     * Moves the last-message pointer forward only, ordered by {@code (time, messageId)}
     * — the same rule the backfill applies to history. Counting a message and pointing
     * at it are separate on purpose: a message created earlier may be recorded later,
     * and it must never rewind the thread's preview or its place in the list.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE chat_conversation SET last_message_id = :messageId, last_message_time = :time "
            + "WHERE party_a_type = :typeA AND party_a_id = :idA "
            + "AND party_b_type = :typeB AND party_b_id = :idB AND is_deleted = 0 "
            + "AND (last_message_id IS NULL OR last_message_time < :time "
            + "OR (last_message_time = :time AND COALESCE(last_message_id, 0) < :messageId))",
            nativeQuery = true)
    int advanceLastMessage(@Param("typeA") String typeA, @Param("idA") Long idA,
                           @Param("typeB") String typeB, @Param("idB") Long idB,
                           @Param("messageId") Long messageId, @Param("time") LocalDateTime time);
}
