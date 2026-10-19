package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

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
     * Records a message on the thread in one statement. A bulk update keeps two
     * senders on the same thread from overwriting each other's revision with a
     * read-modify-write cycle; 0 rows updated means the thread does not exist yet.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ChatConversation c SET c.lastMessageId = :messageId, c.lastMessageTime = :time, "
            + "c.messageCount = c.messageCount + 1, c.revision = c.revision + 1, c.updateTime = :time "
            + "WHERE c.partyAType = :typeA AND c.partyAId = :idA "
            + "AND c.partyBType = :typeB AND c.partyBId = :idB")
    int recordMessage(@Param("typeA") String typeA, @Param("idA") Long idA,
                      @Param("typeB") String typeB, @Param("idB") Long idB,
                      @Param("messageId") Long messageId, @Param("time") LocalDateTime time);
}
