package com.example.medical.module.chat.service;

import com.example.medical.module.chat.dto.SafetyNoticeVO;
import org.springframework.stereotype.Service;

/**
 * The deterministic safety entry point for messaging (Round 54.4).
 * <p>
 * The notice shown next to the composer is fixed and reviewed, not generated: it must
 * appear whether or not an agent, a model or a retrieval service is available, and the
 * channel must never imply a real-time emergency response it cannot provide. A message
 * the rules do not classify is not evidence that the message is safe, so nothing here
 * tries to judge content — it only states the boundary.
 */
@Service
public class SafetyNoticeService {

    /** Bump together with the wording; the version travels with the notice. */
    public static final int NOTICE_VERSION = 1;

    public static final String EMERGENCY_NOTICE =
            "If this is a medical emergency, call 911 or go to the nearest emergency department now. "
            + "This messaging channel is not monitored around the clock and is not an emergency service.";

    public static final String RESPONSE_NOTICE =
            "Messages are read by clinic staff during business hours, so a reply may take time. "
            + "Nothing sent here is medical advice until a clinician has reviewed it.";

    public SafetyNoticeVO current() {
        return SafetyNoticeVO.of(NOTICE_VERSION, EMERGENCY_NOTICE, RESPONSE_NOTICE);
    }
}
