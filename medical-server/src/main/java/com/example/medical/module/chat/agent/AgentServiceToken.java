package com.example.medical.module.chat.agent;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The directional credential the agent service presents (Round 55.3).
 * <p>
 * It authenticates the service, not a patient: passing it proves which process is calling,
 * and it is never enough on its own to reach patient data — that needs the capability bound
 * to the run. A blank configured token denies every internal call rather than opening them.
 */
@Component
@RequiredArgsConstructor
public class AgentServiceToken {

    private final AgentDispatchProperties properties;

    public void require(String presented) {
        String expected = properties.getServiceToken();
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "agent service access is not configured");
        }
        if (presented == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "invalid agent service credential");
        }
    }
}
