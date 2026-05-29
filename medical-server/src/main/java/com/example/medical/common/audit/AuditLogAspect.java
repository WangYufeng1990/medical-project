package com.example.medical.common.audit;

import com.example.medical.common.audit.repository.AuditLogRepository;
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

import java.time.LocalDateTime;
import java.util.StringJoiner;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String[] TARGET_PARAM_NAMES = {"id", "patientId", "prescriptionId", "appointmentId"};

    private final AuditLogRepository auditLogRepository;
    private final HttpServletRequest request;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            AuditLog auditLog = new AuditLog();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser lu) {
                auditLog.setUserId(lu.getUserId());
                auditLog.setUsername(lu.getUsername());
            }
            auditLog.setModule(auditable.module());
            auditLog.setAction(auditable.action());
            auditLog.setTargetId(resolveTargetId(joinPoint));
            auditLog.setDetail(buildDetail(joinPoint));
            auditLog.setIp(request.getRemoteAddr());
            auditLog.setCreateTime(LocalDateTime.now());
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Audit log write failed for action={} on module={}. "
                    + "This may indicate a database issue requiring immediate attention.",
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

    private String buildDetail(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getMethod().getName();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StringJoiner sj = new StringJoiner(", ", method + "(", ")");
        for (int i = 0; i < paramNames.length; i++) {
            Object value = args[i];
            if (value == null) {
                sj.add(paramNames[i] + "=null");
            } else if (value instanceof String s && s.length() > 100) {
                sj.add(paramNames[i] + "=" + s.substring(0, 97) + "...");
            } else {
                String str = value.toString();
                if (str.length() > 100) {
                    sj.add(paramNames[i] + "=" + str.substring(0, 97) + "...");
                } else {
                    sj.add(paramNames[i] + "=" + str);
                }
            }
        }
        return sj.toString();
    }
}
