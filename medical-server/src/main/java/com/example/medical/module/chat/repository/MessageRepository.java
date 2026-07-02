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

    @Query("SELECT m FROM Message m WHERE ((m.senderId = :userId1 AND m.receiverId = :userId2) " +
            "OR (m.senderId = :userId2 AND m.receiverId = :userId1)) ORDER BY m.createTime ASC")
    List<Message> findMessagesBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT m FROM Message m WHERE ((m.senderId = :userId1 AND m.receiverId = :userId2) " +
            "OR (m.senderId = :userId2 AND m.receiverId = :userId1)) ORDER BY m.createTime ASC")
    Page<Message> findMessagesBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2,
                                       Pageable pageable);

    @Query("SELECT m FROM Message m WHERE (m.senderId = :userId OR m.receiverId = :userId) ORDER BY m.createTime DESC")
    List<Message> findAllMessagesByUser(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM message WHERE (sender_id = :userId OR receiver_id = :userId) AND is_deleted = 0 ORDER BY create_time DESC LIMIT :limit", nativeQuery = true)
    List<Message> findRecentMessagesByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.receiverId = :userId AND m.senderId = :partnerId AND m.isRead = 0")
    int countUnread(@Param("userId") Long userId, @Param("partnerId") Long partnerId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = 1 WHERE m.receiverId = :userId AND m.senderId = :partnerId AND m.isRead = 0")
    int markAsRead(@Param("userId") Long userId, @Param("partnerId") Long partnerId);
}
