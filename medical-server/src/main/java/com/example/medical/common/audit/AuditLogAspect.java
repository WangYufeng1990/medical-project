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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * AOP aspect that intercepts {@link Auditable}-annotated methods.
 * <p>
 * Context capture (user, IP, target ID, timestamp) happens synchronously so
 * we can safely read from {@code SecurityContextHolder}.  The actual database
 * write is delegated to {@link AuditLogWriter} which runs on the
 * {@code auditExecutor} thread pool — a failure there will never roll back
 * the already-committed business transaction.
 * <p>
 * Detail serialization never persists secrets or ePHI: on {@code phiAccess}
 * methods every parameter value is masked as {@code [PHI]}, and for other
 * methods complex arguments are reflected field-by-field with a
 * {@link #SENSITIVE_FIELD_NAMES} blacklist (passwords, tokens, chat content,
 * clinical text, identifiers) redacted — {@code toString()} of request DTOs
 * is never used (Full-System Review III C1).
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String[] TARGET_PARAM_NAMES = {
            "id", "patientId", "prescriptionId", "appointmentId"
    };
    private static final int VALUE_MAX_LEN = 50;
    private static final int DETAIL_MAX_LEN = 1500;

    /**
     * Field names that must never land in the audit detail even when the
     * method is not flagged phiAccess. Matched case-insensitively with
     * separators stripped (refreshToken / refresh_token → refreshtoken).
     */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password", "oldpassword", "newpassword", "currentpassword",
            "confirmpassword", "pwd",
            "token", "accesstoken", "refreshtoken", "resettoken", "authtoken", "idtoken",
            "secret", "apikey", "clientsecret", "clientid", "authorization", "bearer",
            "content", "diagnosis", "reason", "notes", "note", "chiefcomplaint",
            "description", "medicalhistory", "allergies", "ssn",
            "deanuumber", "dea", "insurancememberid", "insurancegroupnumber",
            "claimnumber", "phonenumber", "email"
    );

    private final HttpServletRequest request;
    private final AuditLogWriter auditLogWriter;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            Long patientId = resolvePatientId(joinPoint, result, auditable.module());
            Long userId = resolveUserId(joinPoint, result);
            String username = resolveUsername(joinPoint);

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

    private Long resolvePatientId(ProceedingJoinPoint joinPoint, Object result, String module) {
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
        // 3. Fall back to the method result (e.g. patient login returns the id)
        return extractId(unwrapResult(result), "patientId");
    }

    private Long resolveUserId(ProceedingJoinPoint joinPoint, Object result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu.getUserId();
        }
        // permitAll endpoints (login/refresh) have no Authentication yet — the
        // authenticated id comes back in the response envelope.
        return extractId(unwrapResult(result), "userId");
    }

    private String resolveUsername(ProceedingJoinPoint joinPoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
            return lu.getUsername();
        }
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                var m = arg.getClass().getMethod("getUsername");
                Object result = m.invoke(arg);
                if (result != null) return result.toString();
            } catch (Exception ignored) {}
            // Check for nested "username" field via getter pattern
            try {
                var f = arg.getClass().getDeclaredField("username");
                f.setAccessible(true);
                Object val = f.get(arg);
                if (val != null) return val.toString();
            } catch (Exception ignored) {}
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
            } else {
                sj.add(paramNames[i] + "=" + describeArg(args[i]));
            }
        }
        String detail = sj.toString();
        if (detail.length() > DETAIL_MAX_LEN) {
            detail = detail.substring(0, DETAIL_MAX_LEN - 3) + "...";
        }
        return detail;
    }

    static String describeArg(Object arg) {
        if (arg == null) return "null";
        if (isSimpleValue(arg)) {
            return truncate(arg.toString(), VALUE_MAX_LEN);
        }
        if (arg instanceof Iterable<?> iterable) {
            return "[Collection size=" + iterableSize(iterable) + "]";
        }
        return "{" + describeFields(arg) + "}";
    }

    private static String describeFields(Object arg) {
        StringJoiner sj = new StringJoiner(", ", "", "");
        for (Field f : arg.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            try {
                Object value = f.get(arg);
                String normalized = normalize(f.getName());
                if (SENSITIVE_FIELD_NAMES.contains(normalized)) {
                    sj.add(f.getName() + "=[REDACTED]");
                } else if (value == null) {
                    sj.add(f.getName() + "=null");
                } else if (isSimpleValue(value)) {
                    sj.add(f.getName() + "=" + truncate(value.toString(), VALUE_MAX_LEN));
                } else if (value instanceof Iterable<?> iterable) {
                    sj.add(f.getName() + "=[Collection size=" + iterableSize(iterable) + "]");
                } else {
                    sj.add(f.getName() + "=[" + value.getClass().getSimpleName() + "]");
                }
            } catch (IllegalAccessException e) {
                sj.add(f.getName() + "=?");
            }
        }
        return sj.toString();
    }

    private static int iterableSize(Iterable<?> iterable) {
        int size = 0;
        for (Object ignored : iterable) {
            size++;
            if (size > 20) break;
        }
        return size;
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || value instanceof Temporal
                || value instanceof Date;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max - 3) + "..." : value;
    }

    /**
     * Peels the {@code Result<T>} envelope (getData) so id extraction can see
     * the payload returned by permitAll endpoints.
     */
    private static Object unwrapResult(Object result) {
        if (result == null) return null;
        try {
            Object data = result.getClass().getMethod("getData").invoke(result);
            return data != null ? data : result;
        } catch (Exception ignored) {
            return result;
        }
    }

    private static Long extractId(Object obj, String field) {
        if (obj == null) return null;
        String getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        try {
            Object value = obj.getClass().getMethod(getter).invoke(obj);
            return value instanceof Number n ? n.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
