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

    @Query("SELECT m FROM Message m WHERE ((m.senderId = :userId AND m.senderType = :type) " +
            "OR (m.receiverId = :userId AND m.receiverType = :type)) ORDER BY m.createTime DESC")
    List<Message> findRecentMessagesByUser(@Param("userId") Long userId, @Param("type") String type,
                                           Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.receiverType = :type " +
            "AND m.senderId = :partnerId AND m.senderType = :partnerType AND m.isRead = 0")
    int countUnread(@Param("userId") Long userId, @Param("type") String type,
                    @Param("partnerId") Long partnerId, @Param("partnerType") String partnerType);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.receiverType = :type AND m.isRead = 0")
    int countUnreadByUser(@Param("userId") Long userId, @Param("type") String type);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = 1 WHERE m.receiverId = :userId AND m.receiverType = :type " +
            "AND m.senderId = :partnerId AND m.senderType = :partnerType AND m.isRead = 0")
    int markAsRead(@Param("userId") Long userId, @Param("type") String type,
                   @Param("partnerId") Long partnerId, @Param("partnerType") String partnerType);
}
