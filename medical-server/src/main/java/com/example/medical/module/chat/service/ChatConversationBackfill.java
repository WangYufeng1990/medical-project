package com.example.medical.module.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates the conversation row for every thread that only exists as messages —
 * the historical backfill of Round 54.2, and the upgrade step for a database that
 * predates {@code chat_conversation}.
 * <p>
 * Runs after the application is ready (so seed data exists), only inserts pairs
 * that have no row yet (so a second run, or a thread already advanced by live
 * sends, is left alone), and reads the history in one ordered scan: message count,
 * last message id and last activity all come from the same pass, which is what
 * makes "the backfill preserves message count and ownership" checkable.
 * <p>
 * Deliberately plain JDBC: this is a one-off data migration over existing rows, not
 * business logic, and it can therefore be exercised against a standalone database
 * without a Spring context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatConversationBackfill {

    private static final String INSERT = "INSERT INTO chat_conversation (party_a_type, party_a_id, "
            + "party_b_type, party_b_id, last_message_id, last_message_time, message_count, revision, "
            + "create_time, update_time, is_deleted, version) VALUES (?,?,?,?,?,?,?,?,?,?,0,0)";

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int created = backfillMissing();
        if (created > 0) {
            log.info("Chat conversations backfilled from message history: {}", created);
        }
    }

    /** @return the number of conversation rows created. */
    public int backfillMissing() {
        Map<PairKey, History> history = readHistory();
        int created = 0;
        for (Map.Entry<PairKey, History> entry : history.entrySet()) {
            PairKey pair = entry.getKey();
            History row = entry.getValue();
            if (exists(pair)) continue;
            jdbcTemplate.update(INSERT,
                    pair.aType(), pair.aId(), pair.bType(), pair.bId(),
                    row.lastMessageId(), Timestamp.valueOf(row.lastMessageTime()),
                    row.messageCount(), (long) row.messageCount(),
                    Timestamp.valueOf(row.lastMessageTime()), Timestamp.valueOf(row.lastMessageTime()));
            created++;
        }
        return created;
    }

    private Map<PairKey, History> readHistory() {
        Map<PairKey, History> pairs = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, sender_type, sender_id, receiver_type, receiver_id, create_time "
                        + "FROM message WHERE is_deleted = 0 ORDER BY create_time ASC, id ASC",
                rs -> {
                    PairKey key = PairKey.of(
                            new Party(rs.getString("sender_type"), rs.getLong("sender_id")),
                            new Party(rs.getString("receiver_type"), rs.getLong("receiver_id")));
                    LocalDateTime sentAt = rs.getTimestamp("create_time").toLocalDateTime();
                    long messageId = rs.getLong("id");
                    pairs.compute(key, (k, seen) -> seen == null
                            ? new History(1, messageId, sentAt)
                            : new History(seen.messageCount() + 1, messageId, sentAt));
                });
        return pairs;
    }

    private boolean exists(PairKey pair) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_conversation WHERE party_a_type = ? AND party_a_id = ? "
                        + "AND party_b_type = ? AND party_b_id = ? AND is_deleted = 0",
                Integer.class, pair.aType(), pair.aId(), pair.bType(), pair.bId());
        return found != null && found > 0;
    }

    /** The canonical pair of a thread: {@code (type, id)} ascending. */
    private record PairKey(String aType, Long aId, String bType, Long bId) {

        static PairKey of(Party one, Party other) {
            Party first = one.compareTo(other) <= 0 ? one : other;
            Party second = first.equals(one) ? other : one;
            return new PairKey(first.type(), first.id(), second.type(), second.id());
        }
    }

    /** What the history says about one thread: how many, and which message was last. */
    private record History(int messageCount, Long lastMessageId, LocalDateTime lastMessageTime) {}
}
