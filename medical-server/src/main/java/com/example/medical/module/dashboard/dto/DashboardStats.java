package com.example.medical.module.dashboard.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DashboardStats {

    private long totalPatients;
    private long todayAppointments;
    private long scheduledAppointments;
    private BigDecimal monthlyRevenue;
    private long monthlyPrescriptions;
    private long pendingBills;
    private List<Map<String, Object>> appointmentStatusDistribution;
    private List<Map<String, Object>> revenueTrend;

    public static DashboardStats of(long totalPatients, long todayAppointments,
                                     long scheduledAppointments, BigDecimal monthlyRevenue,
                                     long monthlyPrescriptions, long pendingBills,
                                     List<Map<String, Object>> appointmentStatusDistribution,
                                     List<Map<String, Object>> revenueTrend) {
        return new DashboardStats(totalPatients, todayAppointments, scheduledAppointments,
                monthlyRevenue, monthlyPrescriptions, pendingBills,
                appointmentStatusDistribution, revenueTrend);
    }
}
