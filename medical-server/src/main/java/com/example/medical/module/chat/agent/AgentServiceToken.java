package com.example.medical.module.chat.agent;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The credential the agent service presents on its way back into Java (Round 55.3).
 * <p>
 * It is the <em>inbound</em> token: Java's outbound token is a different value, so a
 * credential leaked in one direction cannot be replayed in the other. It authenticates the
 * service, not a patient — it is never enough on its own to reach patient data, which needs
 * the capability bound to the run. A blank configured token denies every internal call
 * rather than opening them.
 */
@Component
@RequiredArgsConstructor
public class AgentServiceToken {

    private final AgentDispatchProperties properties;

    public void require(String presented) {
        String expected = properties.getInboundToken();
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
