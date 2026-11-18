package com.pillpall.med_application.service;

import com.pillpall.med_application.intakes.IntakeEvent;
import com.pillpall.med_application.intakes.IntakeEventRepository;
import com.pillpall.med_application.ml.PatientAdherenceRepository;
import com.pillpall.med_application.prescriptions.PrescriptionRepository;
import com.pillpall.med_application.users.PatientProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdherenceServiceTest {

    @Mock private IntakeEventRepository intakeEventRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PatientAdherenceRepository adherenceRepository;
    @Mock private PatientProfileRepository patientProfileRepository;

    @InjectMocks private AdherenceService service;

    // ── B-OBS-01 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-OBS-01: calculateAdherenceRate — empty list returns 1.0")
    void calculateAdherenceRate_emptyList_returns1() {
        when(intakeEventRepository
                .findByPrescriptionPatientIdAndScheduledAtAfter(eq(10L), any(Instant.class)))
                .thenReturn(List.of());

        double result = service.calculateAdherenceRate(10L);

        assertEquals(1.0, result, 0.0001,
                "No events should yield 100 % adherence (business rule)");
    }

    // ── B-OBS-02 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-OBS-02: calculateAdherenceRate — 2 CONFIRMED / 1 MISSED / 1 PENDING → 0.5")
    void calculateAdherenceRate_mixedStatuses_returns0_5() {
        Instant base = Instant.parse("2026-11-10T10:00:00Z");

        List<IntakeEvent> events = List.of(
                IntakeEvent.builder().id(1L).scheduledAt(base)
                        .status(IntakeEvent.Status.CONFIRMED).build(),
                IntakeEvent.builder().id(2L).scheduledAt(base.plus(Duration.ofHours(1)))
                        .status(IntakeEvent.Status.CONFIRMED).build(),
                IntakeEvent.builder().id(3L).scheduledAt(base.plus(Duration.ofHours(2)))
                        .status(IntakeEvent.Status.MISSED).build(),
                IntakeEvent.builder().id(4L).scheduledAt(base.plus(Duration.ofHours(3)))
                        .status(IntakeEvent.Status.PENDING).build()
        );

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(intakeEventRepository
                .findByPrescriptionPatientIdAndScheduledAtAfter(eq(10L), cutoffCaptor.capture()))
                .thenReturn(events);

        double result = service.calculateAdherenceRate(10L);

        assertEquals(0.5, result, 0.0001,
                "2 CONFIRMED out of 4 total → 0.5");

        // Verify the cutoff is approximately 7 days before now
        Instant capturedCutoff = cutoffCaptor.getValue();
        Instant expectedCutoff = Instant.now().minus(Duration.ofDays(7));
        long diffSeconds = Math.abs(Duration.between(capturedCutoff, expectedCutoff).getSeconds());
        assertTrue(diffSeconds < 5,
                "Cutoff should be ~7 days before now; actual diff = " + diffSeconds + " s");
    }

    // ── B-OBS-04 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-OBS-04: calculateAverageDelay — (10 + 20) / 2 = 15 min")
    void calculateAverageDelay_nominalCase_returns15() {
        Instant scheduled1 = Instant.parse("2026-11-10T10:00:00Z");
        Instant confirmed1 = Instant.parse("2026-11-10T10:10:00Z"); // +10 min
        Instant scheduled2 = Instant.parse("2026-11-10T11:00:00Z");
        Instant confirmed2 = Instant.parse("2026-11-10T11:20:00Z"); // +20 min

        List<IntakeEvent> events = List.of(
                IntakeEvent.builder().id(1L).scheduledAt(scheduled1)
                        .confirmedAt(confirmed1).status(IntakeEvent.Status.CONFIRMED).build(),
                IntakeEvent.builder().id(2L).scheduledAt(scheduled2)
                        .confirmedAt(confirmed2).status(IntakeEvent.Status.CONFIRMED).build()
        );

        when(intakeEventRepository
                .findByPrescriptionPatientIdAndStatus(10L, IntakeEvent.Status.CONFIRMED))
                .thenReturn(events);

        double result = service.calculateAverageDelay(10L);

        assertEquals(15.0, result, 0.001);
    }

    // ── B-OBS-06 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-OBS-06: calculateAverageDelay — null confirmedAt → average only over non-null = 10.0")
    void calculateAverageDelay_nullConfirmedAt_returns10() {
        Instant scheduled1 = Instant.parse("2026-11-10T10:00:00Z");
        Instant confirmed1 = Instant.parse("2026-11-10T10:10:00Z"); // +10 min
        Instant scheduled2 = Instant.parse("2026-11-10T11:00:00Z");
        // second event: confirmedAt = null

        List<IntakeEvent> events = List.of(
                IntakeEvent.builder().id(1L).scheduledAt(scheduled1)
                        .confirmedAt(confirmed1).status(IntakeEvent.Status.CONFIRMED).build(),
                IntakeEvent.builder().id(2L).scheduledAt(scheduled2)
                        .confirmedAt(null).status(IntakeEvent.Status.CONFIRMED).build()
        );

        when(intakeEventRepository
                .findByPrescriptionPatientIdAndStatus(10L, IntakeEvent.Status.CONFIRMED))
                .thenReturn(events);

        double result = service.calculateAverageDelay(10L);

        // Correct business behavior: average only over events with a real confirmedAt → 10.0
        assertEquals(10.0, result, 0.001,
                "Average delay should only consider events with a non-null confirmedAt");
    }
}
