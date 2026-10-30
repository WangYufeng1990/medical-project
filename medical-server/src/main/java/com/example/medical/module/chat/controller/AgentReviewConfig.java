package com.example.medical.module.chat.controller;

import com.example.medical.module.chat.service.AgentReviewAuditInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the audit interceptor for the review endpoints (Round 57.3). */
@Configuration
@RequiredArgsConstructor
public class AgentReviewConfig implements WebMvcConfigurer {

    private final AgentReviewAuditInterceptor audit;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(audit).addPathPatterns("/api/v1/agent/reviews/**");
    }
}
