package com.pillpall.med_application.notifications;

import com.pillpall.med_application.intakes.IntakeEvent;
import com.pillpall.med_application.intakes.IntakeEventRepository;
import com.pillpall.med_application.prescriptions.Prescription;
import com.pillpall.med_application.users.Contact;
import com.pillpall.med_application.users.ContactRepository;
import com.pillpall.med_application.users.PatientProfile;
import com.pillpall.med_application.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock private IntakeEventRepository intakes;
    @Mock private NotificationAttemptRepository attempts;
    @Mock private FcmGateway fcm;
    @Mock private EmailGateway email;
    @Mock private SmsGateway sms;
    @Mock private ContactRepository contacts;

    @InjectMocks private ReminderService service;

    private static final int WINDOW_MINUTES = 60;

    @BeforeEach
    void injectConfig() {
        ReflectionTestUtils.setField(service, "intakeWindowMinutes", WINDOW_MINUTES);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static IntakeEvent buildPendingEvent(Long id, Instant scheduledAt,
                                                  String medName, String token) {
        User user = User.builder().deviceToken(token).build();
        PatientProfile patient = PatientProfile.builder().id(1L).user(user).build();
        Prescription rx = Prescription.builder()
                .patient(patient).medicationName(medName).build();
        return IntakeEvent.builder()
                .id(id).prescription(rx).scheduledAt(scheduledAt)
                .status(IntakeEvent.Status.PENDING).build();
    }

    private static List<NotificationAttempt> buildFcmAttempts(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> NotificationAttempt.builder()
                        .channel("FCM").success(true).build())
                .toList();
    }

    // ── B-RAP-01 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-RAP-01: first FCM reminder — sent and logged")
    void firstFcmReminder_sentAndLogged() {
        IntakeEvent event = buildPendingEvent(
                1L,
                Instant.now().minus(Duration.ofMinutes(2)),
                "Doliprane",
                "test-123");

        // No overdue events
        when(intakes.findByStatusAndScheduledAtBefore(
                eq(IntakeEvent.Status.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        // The event is in the pending window
        when(intakes.findByStatusAndScheduledAtBetween(
                eq(IntakeEvent.Status.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));

        // No prior attempts
        when(attempts.findByIntakeEventId(1L)).thenReturn(List.of());

        // FCM succeeds
        when(fcm.sendToToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        service.processReminders();

        // Verify FCM was sent to the correct token with the medication name
        verify(fcm).sendToToken(
                eq("test-123"),
                eq("Rappel de prise"),
                contains("Doliprane"),
                eq("1"));

        // Verify the attempt was logged
        ArgumentCaptor<NotificationAttempt> captor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(attempts).save(captor.capture());
        NotificationAttempt logged = captor.getValue();
        assertEquals("FCM", logged.getChannel());
        assertTrue(logged.isSuccess());
    }

    // ── B-RAP-02 ────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "at {0} min with {1} prior FCM attempt(s)")
    @MethodSource("spacedReminderCases")
    @DisplayName("B-RAP-02: spaced FCM reminders at 15 / 30 / 45 min")
    void spacedReminders_fcmSentAtCorrectIntervals(int minutesSince, int priorFcmCount) {
        // Schedule with a 30 s margin so toMinutes() truncation stays on our side
        IntakeEvent event = buildPendingEvent(
                10L,
                Instant.now().minus(Duration.ofMinutes(minutesSince).plusSeconds(30)),
                "Aspirine",
                "tok-spaced");

        when(intakes.findByStatusAndScheduledAtBefore(
                eq(IntakeEvent.Status.PENDING), any(Instant.class)))
                .thenReturn(List.of());
        when(intakes.findByStatusAndScheduledAtBetween(
                eq(IntakeEvent.Status.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event));
        when(attempts.findByIntakeEventId(10L))
                .thenReturn(buildFcmAttempts(priorFcmCount));
        when(fcm.sendToToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        service.processReminders();

        verify(fcm, times(1))
                .sendToToken(eq("tok-spaced"), anyString(), contains("Aspirine"), eq("10"));

        ArgumentCaptor<NotificationAttempt> captor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(attempts).save(captor.capture());
        assertEquals("FCM", captor.getValue().getChannel());
    }

    private static Stream<Arguments> spacedReminderCases() {
        return Stream.of(
                Arguments.of(15, 1),
                Arguments.of(30, 2),
                Arguments.of(45, 3)
        );
    }

    // ── B-RAP-04 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-RAP-04: overdue intake → status becomes MISSED, no FCM sent")
    void missedIntake_statusSetNoFcm() {
        IntakeEvent event = buildPendingEvent(
                20L,
                Instant.now().minus(Duration.ofMinutes(WINDOW_MINUTES + 1)),
                "Metformine",
                "tok-miss");

        when(intakes.findByStatusAndScheduledAtBefore(
                eq(IntakeEvent.Status.PENDING), any(Instant.class)))
                .thenReturn(List.of(event));
        when(intakes.findByStatusAndScheduledAtBetween(
                eq(IntakeEvent.Status.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        service.processReminders();

        assertEquals(IntakeEvent.Status.MISSED, event.getStatus(),
                "Overdue event must be marked MISSED");
        verifyNoInteractions(fcm);
    }

    // ── B-RAP-05 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-RAP-05: escalation — email + SMS sent when intake missed with 4+ FCM attempts")
    void escalation_emailAndSmsSentOnMiss() {
        IntakeEvent event = buildPendingEvent(
                42L,
                Instant.now().minus(Duration.ofMinutes(WINDOW_MINUTES + 1)),
                "Doliprane",
                "tok-esc");

        Contact contact = Contact.builder()
                .id(1L)
                .patient(event.getPrescription().getPatient())
                .name("Maman")
                .email("maman@example.com")
                .phone("+33612345678")
                .build();

        // The event is overdue → toMiss query returns it
        when(intakes.findByStatusAndScheduledAtBefore(
                eq(IntakeEvent.Status.PENDING), any(Instant.class)))
                .thenReturn(List.of(event));
        when(intakes.findByStatusAndScheduledAtBetween(
                eq(IntakeEvent.Status.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        lenient().when(attempts.findByIntakeEventId(42L))
                .thenReturn(buildFcmAttempts(4));
        lenient().when(contacts.findByPatientId(1L))
                .thenReturn(List.of(contact));
        lenient().when(email.send(anyString(), anyString(), anyString()))
                .thenReturn(true);
        lenient().when(sms.send(anyString(), anyString()))
                .thenReturn(true);

        service.processReminders();

        // Status must be MISSED regardless
        assertEquals(IntakeEvent.Status.MISSED, event.getStatus());

        verify(email).send(
                eq("maman@example.com"),
                anyString(),
                contains("Doliprane"));
        verify(sms).send(
                eq("+33612345678"),
                contains("Doliprane"));

        // Two attempts logged (EMAIL + SMS)
        ArgumentCaptor<NotificationAttempt> captor =
                ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(attempts, times(2)).save(captor.capture());
        List<NotificationAttempt> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(a -> "EMAIL".equals(a.getChannel()) && a.isSuccess()));
        assertTrue(saved.stream().anyMatch(a -> "SMS".equals(a.getChannel()) && a.isSuccess()));
    }
}
