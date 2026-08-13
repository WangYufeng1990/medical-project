package com.example.medical.common.config;

import com.example.medical.security.JwtClaimMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtClaimMapper jwtClaimMapper;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // H2 console is only reachable when the h2 profile is active — never
        // expose an empty-password DB console from the prod profile.
        boolean h2ConsoleEnabled = java.util.Arrays.stream(activeProfiles.split(","))
                .anyMatch("h2"::equals);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentTypeOptions(cfg -> {})
                        .frameOptions(frame -> frame.sameOrigin())
                        .xssProtection(xss -> {})
                        .cacheControl(cache -> {})
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                                    "/api/v1/patient/login", "/api/v1/patient/refresh",
                                    "/api/v1/patient/forgot-password", "/api/v1/patient/reset-password",
                                    "/api/v1/fhir/metadata",
                                    "/api/v1/chat/subscribe").permitAll()
                            .requestMatchers("/doc.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**").permitAll();
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtClaimMapper)));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0) {
            origins = new String[]{"http://localhost:5173"};
        }
        config.setAllowedOriginPatterns(List.of(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
