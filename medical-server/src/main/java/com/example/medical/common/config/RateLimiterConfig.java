package com.example.medical.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfig {

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.export-per-hour:5}")
    private long exportPerHour;

    @Bean
    public FilterRegistrationBean<Filter> loginRateLimiter(RedissonClient redissonClient) {
        Filter filter = (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            if (httpReq.getRequestURI().contains("/login") && "POST".equalsIgnoreCase(httpReq.getMethod())) {
                String key = "rate:login:" + httpReq.getRemoteAddr();
                RRateLimiter limiter = redissonClient.getRateLimiter(key);
                limiter.trySetRate(RateType.OVERALL, 10, Duration.ofMinutes(1));

                if (!limiter.tryAcquire()) {
                    HttpServletResponse httpResp = (HttpServletResponse) response;
                    httpResp.setStatus(429);
                    httpResp.setContentType("application/json");
                    httpResp.getWriter().write("{\"code\":429,\"message\":\"Too many login attempts. Please wait.\"}");
                    return;
                }
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/v1/auth/login", "/api/v1/patient/login");
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<Filter> refreshRateLimiter(RedissonClient redissonClient) {
        Filter filter = (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String key = "rate:refresh:" + httpReq.getRemoteAddr();
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, 20, Duration.ofMinutes(1));

            if (!limiter.tryAcquire()) {
                HttpServletResponse httpResp = (HttpServletResponse) response;
                httpResp.setStatus(429);
                httpResp.setContentType("application/json");
                httpResp.getWriter().write("{\"code\":429,\"message\":\"Token refresh rate limit exceeded.\"}");
                return;
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/v1/auth/refresh", "/api/v1/patient/refresh");
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<Filter> exportRateLimiter(RedissonClient redissonClient) {
        Filter filter = (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String key = "rate:export:" + httpReq.getRemoteAddr();
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, exportPerHour, Duration.ofHours(1));

            if (!limiter.tryAcquire()) {
                HttpServletResponse httpResp = (HttpServletResponse) response;
                httpResp.setStatus(429);
                httpResp.setContentType("application/json");
                httpResp.getWriter().write("{\"code\":429,\"message\":\"Export rate limit exceeded. Max 5 exports per hour.\"}");
                return;
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/v1/export/*");
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<Filter> passwordResetRateLimiter(RedissonClient redissonClient) {
        Filter filter = (ServletRequest request, ServletResponse response, FilterChain chain) -> {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String key = "rate:password-reset:" + httpReq.getRemoteAddr();
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            limiter.trySetRate(RateType.OVERALL, 5, Duration.ofMinutes(1));

            if (!limiter.tryAcquire()) {
                HttpServletResponse httpResp = (HttpServletResponse) response;
                httpResp.setStatus(429);
                httpResp.setContentType("application/json");
                httpResp.getWriter().write("{\"code\":429,\"message\":\"Too many password reset requests. Please wait.\"}");
                return;
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/v1/patient/forgot-password", "/api/v1/patient/reset-password");
        bean.setOrder(1);
        return bean;
    }
}
