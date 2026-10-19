package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE ((m.senderId = :userId1 AND m.senderType = :type1 " +
            "AND m.receiverId = :userId2 AND m.receiverType = :type2) " +
            "OR (m.senderId = :userId2 AND m.senderType = :type2 " +
            "AND m.receiverId = :userId1 AND m.receiverType = :type1)) ORDER BY m.createTime ASC")
    List<Message> findMessagesBetween(@Param("userId1") Long userId1, @Param("type1") String type1,
                                      @Param("userId2") Long userId2, @Param("type2") String type2);

    @Query("SELECT m FROM Message m WHERE ((m.senderId = :userId1 AND m.senderType = :type1 " +
            "AND m.receiverId = :userId2 AND m.receiverType = :type2) " +
            "OR (m.senderId = :userId2 AND m.senderType = :type2 " +
            "AND m.receiverId = :userId1 AND m.receiverType = :type1)) ORDER BY m.createTime DESC")
    Page<Message> findMessagesBetween(@Param("userId1") Long userId1, @Param("type1") String type1,
                                      @Param("userId2") Long userId2, @Param("type2") String type2,
                                      Pageable pageable);

    /**
     * Unread counts for every partner at once: the conversation list needs one count
     * per row it renders, and asking the database once beats one query per row.
     */
    @Query("SELECT m.senderType, m.senderId, COUNT(m) FROM Message m "
            + "WHERE m.receiverId = :userId AND m.receiverType = :type AND m.isRead = 0 "
            + "GROUP BY m.senderType, m.senderId")
    List<Object[]> countUnreadGroupedBySender(@Param("userId") Long userId, @Param("type") String type);

    @Query("SELECT COUNT(m) FROM Message m WHERE ((m.senderId = :userId1 AND m.senderType = :type1 " +
            "AND m.receiverId = :userId2 AND m.receiverType = :type2) " +
            "OR (m.senderId = :userId2 AND m.senderType = :type2 " +
            "AND m.receiverId = :userId1 AND m.receiverType = :type1))")
    long countBetween(@Param("userId1") Long userId1, @Param("type1") String type1,
                      @Param("userId2") Long userId2, @Param("type2") String type2);

    /**
     * Whether these two typed parties have ever exchanged a message. A recipient
     * who is already in a thread stays reachable even when no appointment or
     * prescription ties them to the patient any more (Round 54.1).
     */
    default boolean hasConversation(Long userId1, String type1, Long userId2, String type2) {
        return countBetween(userId1, type1, userId2, type2) > 0;
    }

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.receiverType = :type AND m.isRead = 0")
    int countUnreadByUser(@Param("userId") Long userId, @Param("type") String type);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = 1 WHERE m.receiverId = :userId AND m.receiverType = :type " +
            "AND m.senderId = :partnerId AND m.senderType = :partnerType AND m.isRead = 0")
    int markAsRead(@Param("userId") Long userId, @Param("type") String type,
                   @Param("partnerId") Long partnerId, @Param("partnerType") String partnerType);
}
