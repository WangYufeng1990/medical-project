package com.example.medical.common.audit;

import com.example.medical.common.audit.repository.AuditLogRepository;
import com.example.medical.common.result.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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

        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        return PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.map(AuditLogVO::fromEntity).getContent());
    }
}
