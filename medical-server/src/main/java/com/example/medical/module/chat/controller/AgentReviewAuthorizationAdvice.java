package com.example.medical.module.chat.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.chat.service.AgentReviewEventService;
import com.example.medical.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records review attempts that authorization refused (Round 57.3 review).
 * <p>
 * The interceptor approach did not work: the global handler consumes the exception, so the
 * interceptor's {@code afterCompletion} receives {@code null}. The denial is logged where it is
 * handled instead, scoped to this controller so the rest of the API keeps its own handler, and only
 * for the review paths that carry a run id.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AgentReviewController.class)
@RequiredArgsConstructor
public class AgentReviewAuthorizationAdvice {

    private static final Pattern RUN = Pattern.compile("/agent/reviews/(\\d+)/");

    private final AgentReviewEventService eventLog;

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException denied,
                                           HttpServletRequest request) {
        log.warn("Review access denied: {}", denied.getMessage());
        Matcher match = RUN.matcher(request.getRequestURI());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (match.find() && auth != null && auth.getPrincipal() instanceof LoginUser user) {
            eventLog.recordRefusal(Long.valueOf(match.group(1)), "DENIED", user.getUserId(),
                    "AUTHORIZATION");
        }
        return Result.fail(403, "Access denied");
    }
}
