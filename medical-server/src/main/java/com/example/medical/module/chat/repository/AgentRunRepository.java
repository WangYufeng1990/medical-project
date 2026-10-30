package com.example.medical.module.chat.repository;

import com.example.medical.module.chat.entity.AgentRun;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * The row a reviewer is deciding on, locked for the transaction: two concurrent approvals must
     * not both publish, and the lock is what serialises them (Round 57.2).
     */
    /** A human reply makes every waiting draft for that patient stale (Round 57.3). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE AgentRun r SET r.state = :to "
            + "WHERE r.state = :from AND r.patientId = :patientId")
    int supersedeWaiting(@org.springframework.data.repository.query.Param("patientId") Long patientId,
                         @org.springframework.data.repository.query.Param("from") String from,
                         @org.springframework.data.repository.query.Param("to") String to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM AgentRun r WHERE r.id = :id")
    java.util.Optional<AgentRun> findByIdForUpdate(@org.springframework.data.repository.query.Param("id")
                                                          Long id);

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
    /**
     * Ends the attempt that is still running. Both the generation and the source state are
     * checked: a result that arrived while the dispatch call was in flight has already moved
     * the row to WAITING_REVIEW, and a late failure report must not write over it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :state, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = :error, r.updateTime = :now "
            + "WHERE r.id = :id AND r.executionGeneration = :generation AND r.state = :running")
    int transition(@Param("id") Long id, @Param("state") String state,
                   @Param("error") String error, @Param("generation") Long generation,
                   @Param("now") LocalDateTime now, @Param("running") String running);

    /**
     * Requeues a failed attempt — again only while it is still the running one, so an attempt
     * whose result was accepted cannot be put back in the queue by its own late failure.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :pending, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.nextAttemptAt = :nextAttemptAt, r.lastError = :error, r.updateTime = :now "
            + "WHERE r.id = :id AND r.executionGeneration = :generation AND r.state = :running")
    int retryLater(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                   @Param("error") String error, @Param("generation") Long generation,
                   @Param("now") LocalDateTime now, @Param("pending") String pending,
                   @Param("running") String running);

    /**
     * The worker handed a result over; the run waits for review (Round 57 defines the draft).
     * The lease and the deadline are checked in the same statement as the write, so a result
     * cannot slip in between a passing authorization check and a row that has since expired.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AgentRun r SET r.state = :state, r.leaseOwner = NULL, r.leaseExpiresAt = NULL, "
            + "r.lastError = NULL, r.updateTime = :now "
            + "WHERE r.id = :id AND r.executionGeneration = :generation "
            + "AND r.state = :running AND r.leaseExpiresAt > :now AND r.deadlineAt > :now")
    int complete(@Param("id") Long id, @Param("generation") Long generation,
                 @Param("state") String state, @Param("now") LocalDateTime now,
                 @Param("running") String running);

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
