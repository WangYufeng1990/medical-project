package com.example.medical.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Deployment guard (Review III C5/C6). The application must never boot in an
 * ambiguous or insecure configuration. Fails fast when:
 * <ul>
 *   <li>no active Spring profile is set (previously defaulted to h2 → dev-mode
 *       with hardcoded keys and an open H2 console);</li>
 *   <li>the active profile is not one of prod / dev / h2;</li>
 *   <li>prod lacks required secrets (AES_KEY, JWT_SIGNING_KEY, DB_USER, DB_PASSWORD);</li>
 *   <li>prod reuses AES_KEY as the JWT signing key (no key separation);</li>
 *   <li>prod runs with dev-mode enabled.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProdGuard {

    private static final Set<String> ALLOWED_PROFILES = Set.of("prod", "dev", "h2");

    private final Environment environment;

    @PostConstruct
    void validate() {
        List<String> profiles = List.of(environment.getActiveProfiles());
        if (profiles.isEmpty()) {
            fail("No active Spring profile. Set SPRING_PROFILES_ACTIVE=prod|dev|h2 explicitly — "
                    + "the app refuses to boot with a default profile (Review III C5).");
        }
        for (String p : profiles) {
            if (!ALLOWED_PROFILES.contains(p)) {
                fail("Unsupported active profile: " + p + ". Allowed: prod, dev, h2.");
            }
        }

        boolean prod = profiles.contains("prod");
        boolean dev = profiles.contains("dev") || profiles.contains("h2");

        boolean devMode = Boolean.parseBoolean(environment.getProperty("app.security.dev-mode", "false"));
        if (prod && devMode) {
            fail("app.security.dev-mode must be false in the prod profile (Review III C5).");
        }
        if (dev && !devMode) {
            log.warn("Profile {} active with app.security.dev-mode=false — "
                    + "local JWT signing (dev-jwt-secret) will not be available.", String.join(",", profiles));
        }

        if (prod) {
            require("AES_KEY");
            require("JWT_SIGNING_KEY");
            require("DB_USER");
            require("DB_PASSWORD");
            String aesKey = environment.getProperty("AES_KEY", "");
            String jwtKey = environment.getProperty("JWT_SIGNING_KEY", "");
            if (!aesKey.isBlank() && aesKey.equals(jwtKey)) {
                fail("JWT_SIGNING_KEY must be independent from AES_KEY (key separation, Review III C6).");
            }
        }

        log.info("Deployment guard passed for profile(s): {}", String.join(",", profiles));
    }

    private void require(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            fail("Required environment variable " + key + " is not set (profile=prod).");
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException("Startup blocked by ProdGuard: " + message);
    }
}
