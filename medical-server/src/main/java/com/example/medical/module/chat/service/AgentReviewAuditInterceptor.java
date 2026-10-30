package com.example.medical.module.chat.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records a review attempt that @PreAuthorize refused (Round 57.3 review): the denial happens before
 * the controller method, so the controller cannot log it. Only the run id, the actor and a fixed
 * classification are stored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentReviewAuditInterceptor implements HandlerInterceptor {

    private static final Pattern RUN = Pattern.compile("/agent/reviews/(\\d+)/");

    private final AgentReviewEventService eventLog;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception failure) {
        if (!(failure instanceof AccessDeniedException)) return;
        Matcher match = RUN.matcher(request.getRequestURI());
        if (!match.find()) return;
        Long runId = Long.valueOf(match.group(1));
        Long actorId = currentUserId();
        if (actorId == null) return;
        eventLog.recordRefusal(runId, "DENIED", actorId, "AUTHORIZATION");
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof com.example.medical.security.LoginUser user)) {
            return null;
        }
        // The interceptor runs after the security context is cleared in some chains; the principal
        // may be a plain string in others, in which case the actor is unknown and nothing is stored.
        return user.getUserId();
    }
}
