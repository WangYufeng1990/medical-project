package com.example.medical.module.quality.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QualityScheduler {

    private final QualityMeasureService qualityMeasureService;

    @Scheduled(cron = "0 0 2 * * *")
    public void calculateDaily() {
        log.info("Starting daily eCQM calculation...");
        qualityMeasureService.calculateAllMeasures();
    }
}
