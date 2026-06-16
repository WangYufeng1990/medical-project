package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.AuditLogWriter;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientAuthControllerTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientAuthRepository patientAuthRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtEncoder jwtEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private AuditLogWriter auditLogWriter;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private PatientAuthController controller;

    private static final long PATIENT_ID = 100L;
    private static final String USERNAME = "patient1";
    private static final String RAW_PASSWORD = "patient123";
    private static final String BCRYPT_HASH = "$2a$10$hashed";
    private static final String TOKEN_VALUE = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwYXRpZW50MSJ9.sig";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "accessTokenExpirySeconds", 7200L);
    }

    // ──────────────────────────────────────────────────────
    // LOGIN — success
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldReturnToken_whenCredentialsValid() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Patient patient = patientWith(PATIENT_ID, "James Anderson");
        Jwt jwt = jwtWith(TOKEN_VALUE);

        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        Result<PatientAuthController.PatientLoginResponse> result =
                controller.login(req(USERNAME, RAW_PASSWORD));

        assertEquals(200, result.getCode());
        assertEquals(TOKEN_VALUE, result.getData().getToken());
        assertEquals(PATIENT_ID, result.getData().getPatientId());
        assertEquals("James Anderson", result.getData().getName());
        assertEquals(USERNAME, result.getData().getUsername());
        verify(patientAuthRepository).resetFailedAttempts(auth.getId());
        verify(patientAuthRepository).save(auth);
    }

    @Test
    void login_shouldAllowLogin_whenLockExpired() {
        LocalDateTime expiredLock = LocalDateTime.now().minusMinutes(1);
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, expiredLock);
        Patient patient = patientWith(PATIENT_ID, "James Anderson");
        Jwt jwt = jwtWith(TOKEN_VALUE);

        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(passwordEncoder.matches(RAW_PASSWORD, BCRYPT_HASH)).thenReturn(true);
        when(jwtEncoder.encode(any())).thenReturn(jwt);

        Result<PatientAuthController.PatientLoginResponse> result =
                controller.login(req(USERNAME, RAW_PASSWORD));

        assertEquals(200, result.getCode());
    }

    // ──────────────────────────────────────────────────────
    // LOGIN — user not found
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldReturn401_whenUserNotFound() {
        when(patientAuthRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req("ghost", "any")));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("Invalid username or password", ex.getMessage());
        verify(auditLogWriter).writeAsync(
                isNull(), eq("ghost"), isNull(),
                eq("auth"), eq("PATIENT_LOGIN_FAILED"), eq("ghost"),
                contains("USER_NOT_FOUND"), any(), any(Instant.class));
    }

    @Test
    void userNotFound_and_badPassword_returnSameMessage() {
        // Prevents user enumeration: both cases must say "Invalid username or password"
        when(patientAuthRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BusinessException notFound = assertThrows(BusinessException.class, () ->
                controller.login(req("ghost", "any")));

        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Patient patient = patientWith(PATIENT_ID, "James Anderson");
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(passwordEncoder.matches("wrong", BCRYPT_HASH)).thenReturn(false);

        BusinessException badPw = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, "wrong")));

        assertEquals(notFound.getMessage(), badPw.getMessage());
    }

    // ──────────────────────────────────────────────────────
    // LOGIN — account disabled / locked
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldReturn403_whenAccountDisabled() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 0, null);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, RAW_PASSWORD)));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("Account is disabled"));
        verify(auditLogWriter).writeAsync(
                isNull(), eq(USERNAME), eq(PATIENT_ID),
                eq("auth"), eq("PATIENT_LOGIN_FAILED"), eq(USERNAME),
                contains("ACCOUNT_DISABLED"), any(), any(Instant.class));
    }

    @Test
    void login_shouldReturn403_whenAccountLocked() {
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(10);
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, lockedUntil);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, RAW_PASSWORD)));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("temporarily locked"));
        verify(auditLogWriter).writeAsync(
                isNull(), eq(USERNAME), eq(PATIENT_ID),
                eq("auth"), eq("PATIENT_LOGIN_FAILED"), eq(USERNAME),
                contains("ACCOUNT_LOCKED"), any(), any(Instant.class));
    }

    @Test
    void login_shouldNotCheckPassword_whenAccountDisabled() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 0, null);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));

        assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, RAW_PASSWORD)));

        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ──────────────────────────────────────────────────────
    // LOGIN — bad password
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldReturn401_whenBadPassword() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Patient patient = patientWith(PATIENT_ID, "James Anderson");
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(passwordEncoder.matches("wrongpass", BCRYPT_HASH)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, "wrongpass")));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("Invalid username or password", ex.getMessage());
        verify(patientAuthRepository).incrementFailedAttempts(eq(auth.getId()), any(LocalDateTime.class));
        verify(auditLogWriter).writeAsync(
                isNull(), eq(USERNAME), eq(PATIENT_ID),
                eq("auth"), eq("PATIENT_LOGIN_FAILED"), eq(USERNAME),
                contains("BAD_CREDENTIALS"), any(), any(Instant.class));
        verify(patientAuthRepository, never()).resetFailedAttempts(anyLong());
    }

    @Test
    void login_shouldIncrementFailedAttempts_whenPasswordWrong() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Patient patient = patientWith(PATIENT_ID, "James Anderson");
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(passwordEncoder.matches("bad", BCRYPT_HASH)).thenReturn(false);

        assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, "bad")));

        verify(patientAuthRepository).incrementFailedAttempts(eq(auth.getId()), any(LocalDateTime.class));
        verify(patientAuthRepository, never()).resetFailedAttempts(anyLong());
    }

    // ──────────────────────────────────────────────────────
    // LOGIN — orphaned auth (Patient record deleted)
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldReturn401_whenPatientRecordMissing() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, RAW_PASSWORD)));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("Patient record not found"));
        // Password was valid, but patient record is orphaned.
        // resetFailedAttempts is never reached because patient lookup throws first.
        verify(patientAuthRepository, never()).resetFailedAttempts(anyLong());
        verify(patientAuthRepository, never()).incrementFailedAttempts(anyLong(), any());
    }

    // ──────────────────────────────────────────────────────
    // REFRESH — success
    // ──────────────────────────────────────────────────────

    @Test
    void refresh_shouldReturnNewToken_whenTokenValid() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Jwt oldJwt = oldJwtWith(USERNAME);
        Jwt newJwt = jwtWith("new-token-value");

        when(jwtUtils.validateToken(TOKEN_VALUE)).thenReturn(true);
        when(jwtUtils.parseToken(TOKEN_VALUE)).thenReturn(oldJwt);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(newJwt);

        Result<PatientAuthController.PatientLoginResponse> result =
                controller.refresh(refreshReq(TOKEN_VALUE));

        assertEquals(200, result.getCode());
        assertEquals("new-token-value", result.getData().getToken());
        assertEquals(PATIENT_ID, result.getData().getPatientId());
        assertEquals(USERNAME, result.getData().getUsername());
        assertNull(result.getData().getRefreshToken());
    }

    @Test
    void refresh_shouldUseAuthPatientId_notTokenClaim() {
        // patientId MUST come from PatientAuth, not the JWT claim
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 1, null);
        Jwt oldJwt = oldJwtWith(USERNAME); // no uid claim
        Jwt newJwt = jwtWith("final-token");

        when(jwtUtils.validateToken(TOKEN_VALUE)).thenReturn(true);
        when(jwtUtils.parseToken(TOKEN_VALUE)).thenReturn(oldJwt);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(newJwt);

        Result<PatientAuthController.PatientLoginResponse> result =
                controller.refresh(refreshReq(TOKEN_VALUE));

        assertEquals(PATIENT_ID, result.getData().getPatientId());
    }

    // ──────────────────────────────────────────────────────
    // REFRESH — invalid / expired token
    // ──────────────────────────────────────────────────────

    @Test
    void refresh_shouldReturn401_whenTokenInvalid() {
        when(jwtUtils.validateToken("expired-token")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.refresh(refreshReq("expired-token")));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("Invalid or expired token"));
        verify(jwtUtils, never()).parseToken(anyString());
        verify(patientAuthRepository, never()).findByUsername(anyString());
    }

    // ──────────────────────────────────────────────────────
    // REFRESH — account state checks
    // ──────────────────────────────────────────────────────

    @Test
    void refresh_shouldReturn403_whenAccountDisabled() {
        PatientAuth auth = authWith(PATIENT_ID, USERNAME, BCRYPT_HASH, 0, null);
        Jwt oldJwt = oldJwtWith(USERNAME);

        when(jwtUtils.validateToken(TOKEN_VALUE)).thenReturn(true);
        when(jwtUtils.parseToken(TOKEN_VALUE)).thenReturn(oldJwt);
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.of(auth));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.refresh(refreshReq(TOKEN_VALUE)));

        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("Account is disabled"));
    }

    @Test
    void refresh_shouldReturn401_whenUserNotFound() {
        Jwt oldJwt = oldJwtWith("ghost");

        when(jwtUtils.validateToken(TOKEN_VALUE)).thenReturn(true);
        when(jwtUtils.parseToken(TOKEN_VALUE)).thenReturn(oldJwt);
        when(patientAuthRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.refresh(refreshReq(TOKEN_VALUE)));

        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("User not found"));
    }

    // ──────────────────────────────────────────────────────
    // audit failure resilience
    // ──────────────────────────────────────────────────────

    @Test
    void login_shouldNotFail_whenAuditWriterThrows() {
        when(patientAuthRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("audit down")).when(auditLogWriter)
                .writeAsync(any(), any(), any(), any(), any(), any(), any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                controller.login(req(USERNAME, RAW_PASSWORD)));

        assertEquals("Invalid username or password", ex.getMessage());
    }

    // ──────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────

    private PatientAuth authWith(Long patientId, String username, String password,
                                 int status, LocalDateTime lockedUntil) {
        PatientAuth auth = new PatientAuth();
        auth.setId(patientId);
        auth.setPatientId(patientId);
        auth.setUsername(username);
        auth.setPassword(password);
        auth.setStatus(status);
        auth.setLockedUntil(lockedUntil);
        return auth;
    }

    private Patient patientWith(Long id, String name) {
        Patient patient = new Patient();
        patient.setId(id);
        patient.setName(name);
        return patient;
    }

    private Jwt jwtWith(String tokenValue) {
        return new Jwt(tokenValue, Instant.now(), Instant.now().plusSeconds(7200),
                Map.of("alg", "HS256"), Map.of("sub", USERNAME, "uid", PATIENT_ID));
    }

    private Jwt oldJwtWith(String username) {
        return new Jwt(TOKEN_VALUE, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "HS256"), Map.of("sub", username));
    }

    private PatientAuthController.PatientLoginRequest req(String username, String password) {
        PatientAuthController.PatientLoginRequest r = new PatientAuthController.PatientLoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    private PatientAuthController.PatientRefreshRequest refreshReq(String token) {
        PatientAuthController.PatientRefreshRequest r = new PatientAuthController.PatientRefreshRequest();
        r.setRefreshToken(token);
        return r;
    }
}
