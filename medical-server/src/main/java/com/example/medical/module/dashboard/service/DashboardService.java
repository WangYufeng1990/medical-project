package com.example.medical.module.dashboard.service;

import com.example.medical.module.dashboard.dto.DashboardStats;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    @Cacheable(value = "dashboard", key = "'stats'")
    public DashboardStats getStats() {
        long totalPatients = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient WHERE is_deleted = 0", Long.class);

        long todayAppointments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment WHERE CAST(appointment_time AS DATE) = ? AND is_deleted = 0",
                Long.class, LocalDate.now().toString());

        long scheduledAppointments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment WHERE status = 0 AND is_deleted = 0", Long.class);

        BigDecimal monthlyRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(patient_paid_amount), 0) FROM bill WHERE claim_status = 'PAID' AND pay_time >= ? AND is_deleted = 0",
                BigDecimal.class, LocalDate.now().withDayOfMonth(1).toString());

        long monthlyPrescriptions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE prescription_date >= ? AND is_deleted = 0",
                Long.class, LocalDate.now().withDayOfMonth(1).toString());

        long pendingBills = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bill WHERE claim_status = 'PENDING' AND is_deleted = 0", Long.class);

        List<Map<String, Object>> appointmentStatusDist = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS count FROM appointment WHERE is_deleted = 0 GROUP BY status");

        List<Map<String, Object>> revenueTrend = computeRevenueTrend();

        return DashboardStats.of(totalPatients, todayAppointments, scheduledAppointments,
                monthlyRevenue, monthlyPrescriptions, pendingBills,
                appointmentStatusDist, revenueTrend);
    }

    private List<Map<String, Object>> computeRevenueTrend() {
        LocalDate start = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT pay_time, patient_paid_amount FROM bill WHERE claim_status = 'PAID' AND pay_time >= ? AND is_deleted = 0 ORDER BY pay_time",
                start.toString());

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
