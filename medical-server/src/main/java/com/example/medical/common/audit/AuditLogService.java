package com.example.medical.common.audit;

import com.example.medical.common.audit.repository.AuditLogRepository;
import com.example.medical.common.result.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.medical.common.base.Pages;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public PageResult<AuditLogVO> search(int page, int size,
                                          Long userId, Long patientId,
                                          String module, String action,
                                          LocalDate fromDate, LocalDate toDate) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patientId"), patientId));
            }
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"),
                        LocalDateTime.of(fromDate, LocalTime.MIN)));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"),
                        LocalDateTime.of(toDate, LocalTime.MAX)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = Pages.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        return PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.map(AuditLogVO::fromEntity).getContent());
    }

    public Map<String, List<String>> getDistinctModulesAndActions() {
        List<Object[]> rows = auditLogRepository.findDistinctModulesAndActions();
        List<String> modules = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        for (Object[] row : rows) {
            String m = (String) row[0];
            String a = (String) row[1];
            if (m != null && !modules.contains(m)) modules.add(m);
            if (a != null && !actions.contains(a)) actions.add(a);
        }
        java.util.Collections.sort(modules);
        java.util.Collections.sort(actions);
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("modules", modules);
        result.put("actions", actions);
        return result;
    }

    /**
     * Tamper-evidence check (Review III M2): verifies every row's hash and the
     * prev_hash chain, and says which of the two broke so a rewritten row can be
     * told apart from a chain that was written wrongly in the first place
     * (R53-08: a concurrent append forked the chain, which is not tampering).
     * Returns null when the log is intact.
     */
    public ChainBreak verifyIntegrity() {
        List<AuditLog> rows = auditLogRepository.findAllByOrderByIdAsc();
        String previousRowHash = null;
        for (AuditLog row : rows) {
            String computed = row.computeRowHash();
            if (computed == null || !computed.equals(row.getRowHash())) {
                return new ChainBreak(row.getId(), Reason.ROW_HASH);
            }
            if (previousRowHash != null && !previousRowHash.equals(row.getPrevHash())) {
                return new ChainBreak(row.getId(), Reason.CHAIN);
            }
            previousRowHash = row.getRowHash();
        }
        return null;
    }

    public enum Reason {
        /** The row's own fields no longer hash to its stored digest. */
        ROW_HASH,
        /** The row hashes correctly but does not link to the row before it. */
        CHAIN
    }

    public record ChainBreak(long rowId, Reason reason) {
    }
}
