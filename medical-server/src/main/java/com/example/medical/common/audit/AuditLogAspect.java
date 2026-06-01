package com.example.medical.common.audit;

import com.example.medical.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.StringJoiner;

/**
 * AOP aspect that intercepts {@link Auditable}-annotated methods.
 * <p>
 * Context capture (user, IP, target ID, timestamp) happens synchronously so
 * we can safely read from {@code SecurityContextHolder}.  The actual database
 * write is delegated to {@link AuditLogWriter} which runs on the
 * {@code auditExecutor} thread pool — a failure there will never roll back
 * the already-committed business transaction.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String[] TARGET_PARAM_NAMES = {
            "id", "patientId", "prescriptionId", "appointmentId"
    };
    private static final String[] PATIENT_ID_PARAM_NAMES = {
            "patientId"
    };

    private final HttpServletRequest request;
    private final AuditLogWriter auditLogWriter;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String username = null;
            Long userId = null;
            Long patientId = resolvePatientId(joinPoint, auditable.module());
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
                userId = lu.getUserId();
                username = lu.getUsername();
            }

            String targetId = resolveTargetId(joinPoint);
            String detail = buildDetail(joinPoint, auditable.phiAccess());

            auditLogWriter.writeAsync(
                    userId, username, patientId,
                    auditable.module(), auditable.action(),
                    targetId, detail,
                    request.getRemoteAddr(),
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Audit context capture failed for action={} module={}. "
                    + "Business transaction is NOT affected.",
                    auditable.action(), auditable.module(), e);
        }

        return result;
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (String target : TARGET_PARAM_NAMES) {
            for (int i = 0; i < paramNames.length; i++) {
                if (target.equals(paramNames[i]) && args[i] != null) {
                    return args[i].toString();
                }
            }
        }
        return null;
    }

    private Long resolvePatientId(ProceedingJoinPoint joinPoint, String module) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        // 1. Direct "patientId" parameter
        for (int i = 0; i < paramNames.length; i++) {
            if ("patientId".equals(paramNames[i]) && args[i] instanceof Number n) {
                return n.longValue();
            }
        }
        // 2. In patient module, "id" IS the patient ID
        if ("patient".equals(module)) {
            for (int i = 0; i < paramNames.length; i++) {
                if ("id".equals(paramNames[i]) && args[i] instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return null;
    }

    private String buildDetail(ProceedingJoinPoint joinPoint, boolean phiAccess) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getMethod().getName();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StringJoiner sj = new StringJoiner(", ", method + "(", ")");
        for (int i = 0; i < paramNames.length; i++) {
            if (phiAccess) {
                sj.add(paramNames[i] + "=[PHI]");
            } else if (args[i] == null) {
                sj.add(paramNames[i] + "=null");
            } else {
                String str = args[i].toString();
                sj.add(paramNames[i] + "=" + (str.length() > 100
                        ? str.substring(0, 97) + "..."
                        : str));
            }
        }
        return sj.toString();
    }
}
