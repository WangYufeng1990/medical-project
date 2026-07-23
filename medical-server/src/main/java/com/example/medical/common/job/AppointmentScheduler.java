package com.example.medical.common.job;

import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentScheduler {

    private final AppointmentRepository appointmentRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markNoShowsOnStartup() {
        log.info("Running no-show check on startup");
        doMarkNoShows();
    }

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void markNoShows() {
        doMarkNoShows();
    }

    private void doMarkNoShows() {
        List<Appointment> missed = appointmentRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("status"), 0),
                        cb.lessThan(root.get("appointmentTime"), LocalDateTime.now())
                ),
                org.springframework.data.domain.Sort.unsorted()
        );

        if (!missed.isEmpty()) {
            missed.forEach(a -> a.setStatus(4));
            appointmentRepository.saveAll(missed);
            log.info("Marked {} appointments as no-show", missed.size());
        }
    }
}
