package com.example.medical.module.dashboard.service;

import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.billing.entity.BillClaimStatus;
import com.example.medical.module.dashboard.dto.DashboardStats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregate counters. The queries are raw SQL, so unlike the JPA-backed lists they
 * do not inherit {@code @SQLRestriction} or doctor scoping for free — both are
 * spelled out here, and the numbers are meant to agree with the corresponding
 * list endpoint for the same caller (a doctor's "Total Patients" is the size of
 * their own patient list).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final DoctorPatientScope doctorPatientScope;

    /**
     * The cache key has to carry the caller's scope. It used to be the constant
     * {@code 'stats'}, which is invisible while {@code spring.cache.type: none}
     * (the h2 profile) but under Redis served whichever scope asked first to
     * everybody else.
     */
    public String scopeKey() {
        Set<Long> scoped = doctorPatientScope.resolve();
        return scoped == null ? "ALL" : scoped.stream().sorted().toList().toString();
    }

    @Cacheable(value = "dashboard", key = "'stats:' + #root.target.scopeKey()")
    public DashboardStats getStats() {
        Set<Long> scoped = doctorPatientScope.resolve();
        String byPatientId = filterFor("patient_id", scoped);
        String byPatientRow = filterFor("id", scoped);
        Object[] scopedIds = scoped == null ? new Object[0] : scoped.toArray();

        long totalPatients = query("SELECT COUNT(*) FROM patient WHERE is_deleted = 0" + byPatientRow,
                Long.class, scopedIds);

        long todayAppointments = query(
                "SELECT COUNT(*) FROM appointment WHERE CAST(appointment_time AS DATE) = ? AND is_deleted = 0" + byPatientId,
                Long.class, concat(LocalDate.now().toString(), scopedIds));

        long scheduledAppointments = query(
                "SELECT COUNT(*) FROM appointment WHERE status = 0 AND is_deleted = 0" + byPatientId,
                Long.class, scopedIds);

        BigDecimal monthlyRevenue = query(
                "SELECT COALESCE(SUM(patient_paid_amount), 0) FROM bill WHERE claim_status = '"
                        + BillClaimStatus.PAID.value() + "' AND pay_time >= ? AND is_deleted = 0" + byPatientId,
                BigDecimal.class, concat(LocalDate.now().withDayOfMonth(1).toString(), scopedIds));

        long monthlyPrescriptions = query(
                "SELECT COUNT(*) FROM prescription WHERE prescription_date >= ? AND is_deleted = 0" + byPatientId,
                Long.class, concat(LocalDate.now().withDayOfMonth(1).toString(), scopedIds));

        long pendingBills = query(
                "SELECT COUNT(*) FROM bill WHERE claim_status = '" + BillClaimStatus.PENDING.value()
                        + "' AND is_deleted = 0" + byPatientId,
                Long.class, scopedIds);

        List<Map<String, Object>> appointmentStatusDist = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS count FROM appointment WHERE is_deleted = 0" + byPatientId
                        + " GROUP BY status", scopedIds);

        List<Map<String, Object>> revenueTrend = computeRevenueTrend(scoped);

        return DashboardStats.of(totalPatients, todayAppointments, scheduledAppointments,
                monthlyRevenue, monthlyPrescriptions, pendingBills,
                appointmentStatusDist, revenueTrend);
    }

    /**
     * {@code null} scope = ADMIN, no filter. An EMPTY scope means "no patients",
     * which must yield zero rows — not an unfiltered query (and {@code IN ()} is
     * not valid SQL).
     */
    private static String filterFor(String column, Set<Long> scoped) {
        if (scoped == null) return "";
        if (scoped.isEmpty()) return " AND 1 = 0";
        return " AND " + column + " IN (" + String.join(",", Collections.nCopies(scoped.size(), "?")) + ")";
    }

    private static Object[] concat(Object first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private <T> T query(String sql, Class<T> type, Object... args) {
        return jdbcTemplate.queryForObject(sql, type, args);
    }

    private List<Map<String, Object>> computeRevenueTrend(Set<Long> scoped) {
        LocalDate start = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT pay_time, patient_paid_amount FROM bill WHERE claim_status = '"
                        + BillClaimStatus.PAID.value() + "' AND pay_time >= ? AND is_deleted = 0"
                        + filterFor("patient_id", scoped) + " ORDER BY pay_time",
                concat(start.toString(), scoped == null ? new Object[0] : scoped.toArray()));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, BigDecimal> monthlyData = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(LocalDate.now()); d = d.plusMonths(1)) {
            monthlyData.put(d.format(fmt), BigDecimal.ZERO);
        }

        for (Map<String, Object> row : rows) {
            Object payTimeObj = row.get("pay_time");
            Object amountObj = row.get("patient_paid_amount");
            if (payTimeObj != null && amountObj != null) {
                String month = payTimeObj.toString().substring(0, 7);
                BigDecimal amount = amountObj instanceof BigDecimal ? (BigDecimal) amountObj : new BigDecimal(amountObj.toString());
                monthlyData.merge(month, amount, BigDecimal::add);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        monthlyData.forEach((month, revenue) -> result.add(Map.of("month", month, "revenue", revenue)));
        return result;
    }
}
