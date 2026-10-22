package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The durable queue's queries (Round 55.2). Every state transition is a guarded UPDATE, so
 * a stale reader cannot apply it twice: the number of rows updated says whether this caller
 * won.
 */
public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {

    Optional<AgentRun> findByMessageId(Long messageId);

    boolean existsByMessageId(Long messageId);

    @Query("SELECT r.id FROM AgentRun r WHERE r.state = :state AND r.nextAttemptAt <= :now "
            + "AND r.deadlineAt > :now AND r.attempt < r.maxAttempts "
            + "ORDER BY r.nextAttemptAt ASC, r.id ASC")
    List<Long> findDueIds(@Param("state") String state, @Param("now") LocalDateTime now,
                          Pageable pageable);

    /**
     * Takes the lease. The attempt and generation move here, in the same statement that
     * decides who owns the job, so a dispatcher that loses the race simply updates 0 rows.
     */
    /**
     * Takes the lease. The conditions are the whole claim policy: due, still inside its
     * deadline and not out of attempts. Checking any of them only in Java would let a job
     * that expired while this batch was busy be sent to the service anyway.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :running, r.leaseOwner = :owner, "
            + "r.leaseExpiresAt = :leaseUntil, r.attempt = r.attempt + 1, "
            + "r.executionGeneration = r.executionGeneration + 1, r.updateTime = :now "
            + "WHERE r.id = :id AND r.state = :pending AND r.nextAttemptAt <= :now "
            + "AND r.deadlineAt > :now AND r.attempt < r.maxAttempts")
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now,
              @Param("running") String running, @Param("pending") String pending);

    /** Pending jobs that can never be attempted again end here rather than waiting. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :failed, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = :reason, r.updateTime = :now "
            + "WHERE r.state = :pending AND r.attempt >= r.maxAttempts")
    int failExhaustedPending(@Param("now") LocalDateTime now, @Param("pending") String pending,
                             @Param("failed") String failed, @Param("reason") String reason);

    /**
     * A job whose last permitted attempt is still leased when the lease runs out can never
     * be retried; without this it would sit in RUNNING until the overall deadline.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :failed, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = :reason, r.updateTime = :now "
            + "WHERE r.state = :running AND r.leaseExpiresAt < :now AND r.attempt >= r.maxAttempts")
    int failExhaustedRunning(@Param("now") LocalDateTime now, @Param("running") String running,
                             @Param("failed") String failed, @Param("reason") String reason);

    /**
     * Acknowledges a dispatch. The lease is deliberately kept: it now means "handed to the
     * service, awaiting a result", and once it runs out the reconcile sweep is what decides
     * whether the job is retried.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :state, r.lastError = NULL, r.updateTime = :now "
            + "WHERE r.id = :id")
    int markState(@Param("id") Long id, @Param("state") String state, @Param("now") LocalDateTime now);

    /** Terminal states release the lease so nothing can reclaim them. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :state, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = :error, r.updateTime = :now WHERE r.id = :id")
    int transition(@Param("id") Long id, @Param("state") String state,
                   @Param("error") String error, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :pending, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.nextAttemptAt = :nextAttemptAt, r.lastError = :error, r.updateTime = :now "
            + "WHERE r.id = :id")
    int retryLater(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                   @Param("error") String error, @Param("now") LocalDateTime now,
                   @Param("pending") String pending);

    /**
     * Puts jobs whose lease ran out back in the queue. A worker that died mid-dispatch
     * leaves RUNNING behind; without this sweep the row would never be looked at again.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :pending, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.updateTime = :now WHERE r.state = :running AND r.leaseExpiresAt < :now "
            + "AND r.attempt < r.maxAttempts")
    int reclaimExpired(@Param("now") LocalDateTime now, @Param("running") String running,
                       @Param("pending") String pending);

    /** Jobs that outlived their deadline end rather than being retried forever. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :failed, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = :reason, r.updateTime = :now "
            + "WHERE r.deadlineAt < :now AND r.state IN (:pending, :running)")
    int expireDeadlines(@Param("now") LocalDateTime now, @Param("pending") String pending,
                        @Param("running") String running, @Param("failed") String failed,
                        @Param("reason") String reason);
}
