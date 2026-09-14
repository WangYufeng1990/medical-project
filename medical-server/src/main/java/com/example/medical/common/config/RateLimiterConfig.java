package com.example.medical.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterConfig {

    @Value("${app.rate-limit.export-per-hour:5}")
    private long exportPerHour;

    /**
     * A per-IP limiter for one group of endpoints.
     *
     * @param keyPrefix       Redis key prefix; the effective limit is appended to it
     * @param messageTemplate 429 body, optionally containing one {@code %d} for the limit
     */
    private record Limiter(String keyPrefix, long rate, Duration interval,
                           String messageTemplate, List<String> urlPatterns) {}

    @Bean
    public FilterRegistrationBean<Filter> loginRateLimiter(RedissonClient redissonClient) {
        return register(redissonClient, new Limiter("rate:login", 10, Duration.ofMinutes(1),
                "Too many login attempts. Please wait.",
                List.of("/api/v1/auth/login", "/api/v1/patient/login")));
    }

    @Bean
    public FilterRegistrationBean<Filter> refreshRateLimiter(RedissonClient redissonClient) {
        return register(redissonClient, new Limiter("rate:refresh", 20, Duration.ofMinutes(1),
                "Token refresh rate limit exceeded.",
                List.of("/api/v1/auth/refresh", "/api/v1/patient/refresh")));
    }

    @Bean
    public FilterRegistrationBean<Filter> exportRateLimiter(RedissonClient redissonClient) {
        return register(redissonClient, new Limiter("rate:export", exportPerHour, Duration.ofHours(1),
                "Export rate limit exceeded. Max %d exports per hour.",
                List.of("/api/v1/export/*")));
    }

    @Bean
    public FilterRegistrationBean<Filter> passwordResetRateLimiter(RedissonClient redissonClient) {
        return register(redissonClient, new Limiter("rate:password-reset", 5, Duration.ofMinutes(1),
                "Too many password reset requests. Please wait.",
                List.of("/api/v1/patient/forgot-password", "/api/v1/patient/reset-password")));
    }

    /**
     * The rate and interval are part of the Redis key on purpose. Redisson's
     * {@code trySetRate} initialises a limiter only when it does not exist yet,
     * and the remaining-permit counter it creates has no TTL — so with a key
     * that omits the limit, changing {@code app.rate-limit.*} would silently
     * keep serving the budget of whatever limit was configured first. Keying by
     * the limit makes the change self-applying; the superseded keys are inert
     * leftovers (see the README for the cleanup one-liner).
     */
    private FilterRegistrationBean<Filter> register(RedissonClient redissonClient, Limiter limiter) {
        Filter filter = (request, response, chain) -> {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String key = "%s:%d:%d:%s".formatted(limiter.keyPrefix(), limiter.rate(),
                    limiter.interval().toSeconds(), httpRequest.getRemoteAddr());

            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            rateLimiter.trySetRate(RateType.OVERALL, limiter.rate(), limiter.interval());

            if (!rateLimiter.tryAcquire()) {
                reject((HttpServletResponse) response, limiter.messageTemplate().formatted(limiter.rate()));
                return;
            }
            chain.doFilter(request, response);
        };

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns(limiter.urlPatterns().toArray(String[]::new));
        bean.setOrder(1);
        return bean;
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":429,\"message\":\"" + message + "\"}");
    }
}
