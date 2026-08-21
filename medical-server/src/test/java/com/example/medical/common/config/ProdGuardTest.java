package com.example.medical.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for Review III C5/C6: the deployment guard must fail fast
 * on a missing/ambiguous profile, missing prod secrets, and AES/JWT key reuse.
 */
class ProdGuardTest {

    @Test
    void failsFastWithoutActiveProfile() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[0]);
        ProdGuard guard = new ProdGuard(env);
        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void failsFastOnUnsupportedProfile() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"staging"});
        ProdGuard guard = new ProdGuard(env);
        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void failsFastInProdWithoutRequiredSecrets() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("app.security.dev-mode", "false")).thenReturn("false");
        when(env.getProperty("AES_KEY", "")).thenReturn("");
        when(env.getProperty("JWT_SIGNING_KEY", "")).thenReturn("");
        ProdGuard guard = new ProdGuard(env);
        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void failsFastWhenJwtKeyEqualsAesKeyInProd() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(env.getProperty("app.security.dev-mode", "false")).thenReturn("false");
        when(env.getProperty("AES_KEY")).thenReturn("shared-secret-key-0123456789abcdef");
        when(env.getProperty("JWT_SIGNING_KEY")).thenReturn("shared-secret-key-0123456789abcdef");
        when(env.getProperty("DB_USER")).thenReturn("user");
        when(env.getProperty("DB_PASSWORD")).thenReturn("pass");
        when(env.getProperty("AES_KEY", "")).thenReturn("shared-secret-key-0123456789abcdef");
        when(env.getProperty("JWT_SIGNING_KEY", "")).thenReturn("shared-secret-key-0123456789abcdef");
        ProdGuard guard = new ProdGuard(env);
        assertThrows(IllegalStateException.class, guard::validate);
    }

    @Test
    void passesForDevAndH2Profiles() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"h2"});
        when(env.getProperty("app.security.dev-mode", "false")).thenReturn("true");
        ProdGuard guard = new ProdGuard(env);
        assertDoesNotThrow(guard::validate);
    }
}
