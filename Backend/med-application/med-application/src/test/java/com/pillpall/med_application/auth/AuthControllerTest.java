package com.pillpall.med_application.auth;

import com.pillpall.med_application.security.JwtService;
import com.pillpall.med_application.users.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository users;
    @Mock private RoleRepository roles;
    @Mock private PatientProfileRepository patients;
    @Mock private DoctorProfileRepository doctors;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwt;

    @InjectMocks private AuthController controller;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Role role(String name) {
        Role r = new Role();
        r.setId(1L);
        r.setName(name);
        return r;
    }

    private AuthController.RegisterReq registerReq(String email, String roleName) {
        AuthController.RegisterReq req = new AuthController.RegisterReq();
        req.setEmail(email);
        req.setPassword("Str0ng!Pass");
        req.setFullName("Test User");
        req.setRole(roleName);
        return req;
    }

    private AuthController.LoginReq loginReq(String email, String password) {
        AuthController.LoginReq req = new AuthController.LoginReq();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(ResponseEntity<?> resp) {
        return (Map<String, Object>) resp.getBody();
    }

    // ── B-AUTH-01a ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-01a: register patient (no doctors) — 200 + token")
    void registerPatient_noDoctors_ok() {
        Role patientRole = role("ROLE_PATIENT");
        AuthController.RegisterReq req = registerReq("new@test.com", "ROLE_PATIENT");

        when(users.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(roles.findByName("ROLE_PATIENT")).thenReturn(Optional.of(patientRole));
        when(encoder.encode("Str0ng!Pass")).thenReturn("hash-test");
        when(jwt.generate(eq("new@test.com"), anyMap())).thenReturn("token-test");

        ResponseEntity<?> resp = controller.register(req);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("token-test", body(resp).get("token"));

        // Verify user is saved with encoded password and enabled
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(users).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("hash-test", saved.getPassword(),
                "Password must be encoded, never stored in clear");
        assertTrue(saved.isEnabled());
        assertEquals("new@test.com", saved.getEmail());

        // Verify patient profile is created (no doctors)
        ArgumentCaptor<PatientProfile> ppCaptor =
                ArgumentCaptor.forClass(PatientProfile.class);
        verify(patients).save(ppCaptor.capture());
        assertNotNull(ppCaptor.getValue().getUser());
    }

    // ── B-AUTH-01b ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-01b: register patient (with 2 doctors) — doctors linked")
    void registerPatient_withDoctors_ok() {
        Role patientRole = role("ROLE_PATIENT");
        AuthController.RegisterReq req = registerReq("doc-link@test.com", "ROLE_PATIENT");
        req.setDoctorIds(List.of(1L, 2L));

        DoctorProfile doc1 = DoctorProfile.builder().id(1L).build();
        DoctorProfile doc2 = DoctorProfile.builder().id(2L).build();

        when(users.findByEmail("doc-link@test.com")).thenReturn(Optional.empty());
        when(roles.findByName("ROLE_PATIENT")).thenReturn(Optional.of(patientRole));
        when(encoder.encode("Str0ng!Pass")).thenReturn("hash-test");
        when(jwt.generate(eq("doc-link@test.com"), anyMap())).thenReturn("token-test");
        when(doctors.findAllById(List.of(1L, 2L))).thenReturn(List.of(doc1, doc2));

        ResponseEntity<?> resp = controller.register(req);

        assertEquals(200, resp.getStatusCode().value());

        ArgumentCaptor<PatientProfile> ppCaptor =
                ArgumentCaptor.forClass(PatientProfile.class);
        verify(patients).save(ppCaptor.capture());
        assertEquals(2, ppCaptor.getValue().getDoctors().size(),
                "Selected doctors must be linked to the patient profile");
    }

    // ── B-AUTH-02 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-02: register with existing email — 400, nothing saved")
    void registerExistingEmail_400() {
        AuthController.RegisterReq req = registerReq("taken@test.com", "ROLE_PATIENT");

        when(users.findByEmail("taken@test.com"))
                .thenReturn(Optional.of(User.builder().email("taken@test.com").build()));

        ResponseEntity<?> resp = controller.register(req);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Email already exists", body(resp).get("error"));

        verify(users, never()).save(any(User.class));
        verify(jwt, never()).generate(anyString(), anyMap());
    }

    // ── B-AUTH-03 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-03: register doctor without specialtyId — 400, no orphan user")
    void registerDoctor_noSpecialty_400_noOrphan() {
        Role doctorRole = role("ROLE_DOCTOR");
        AuthController.RegisterReq req = registerReq("doc@test.com", "ROLE_DOCTOR");
        req.setSpecialtyId(null);

        when(users.findByEmail("doc@test.com")).thenReturn(Optional.empty());
        when(roles.findByName("ROLE_DOCTOR")).thenReturn(Optional.of(doctorRole));
        lenient().when(encoder.encode("Str0ng!Pass")).thenReturn("hash-test");

        ResponseEntity<?> resp = controller.register(req);

        // The 400 IS returned by the current code
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("SpecialtyId is required for doctors", body(resp).get("error"));
        // No orphan user
        verify(users, never()).save(any(User.class));
    }

    // ── B-AUTH-05 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-05: login success — 200 + JWT")
    void loginSuccess_returnsToken() {
        Role patientRole = role("ROLE_PATIENT");
        User user = User.builder()
                .email("user@test.com")
                .password("encoded-pw")
                .roles(Set.of(patientRole))
                .build();

        when(users.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(encoder.matches("correct-pw", "encoded-pw")).thenReturn(true);
        when(jwt.generate(eq("user@test.com"), anyMap())).thenReturn("token-test");

        ResponseEntity<?> resp = controller.login(loginReq("user@test.com", "correct-pw"));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("token-test", body(resp).get("token"));
        verify(jwt).generate(eq("user@test.com"), anyMap());
    }

    // ── B-AUTH-06a ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-06a: login — unknown user → 401 'Invalid credentials'")
    void loginUnknownUser_401() {
        when(users.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.login(loginReq("ghost@test.com", "any"));

        assertEquals(401, resp.getStatusCode().value());
        assertEquals("Invalid credentials", body(resp).get("error"));
        verify(jwt, never()).generate(anyString(), anyMap());
    }

    // ── B-AUTH-06b ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-AUTH-06b: login — wrong password → 401, same shape as unknown user (no info leak)")
    void loginWrongPassword_401() {
        Role patientRole = role("ROLE_PATIENT");
        User user = User.builder()
                .email("real@test.com")
                .password("encoded-pw")
                .roles(Set.of(patientRole))
                .build();

        when(users.findByEmail("real@test.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong-pw", "encoded-pw")).thenReturn(false);

        ResponseEntity<?> resp = controller.login(loginReq("real@test.com", "wrong-pw"));

        assertEquals(401, resp.getStatusCode().value());
        assertEquals("Invalid credentials", body(resp).get("error"),
                "Error message must be identical to unknown-user case (no info leak)");
        verify(jwt, never()).generate(anyString(), anyMap());
    }
}
