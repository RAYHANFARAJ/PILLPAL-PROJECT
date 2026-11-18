package com.pillpall.med_application.intakes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IntakeServiceTest {

    @Mock private IntakeEventRepository repo;

    @InjectMocks private IntakeService service;

    // ── B-PRISE-01 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("B-PRISE-01: confirm — sets CONFIRMED, timestamp, and notes")
    void confirm_setsStatusAndTimestamp() {
        Instant scheduledAt = Instant.parse("2026-11-10T10:00:00Z");
        IntakeEvent event = IntakeEvent.builder()
                .id(100L)
                .scheduledAt(scheduledAt)
                .status(IntakeEvent.Status.PENDING)
                .build();

        when(repo.findById(100L)).thenReturn(Optional.of(event));

        Instant before = Instant.now();

        IntakeEvent result = service.confirm(100L, "prise faite");

        Instant after = Instant.now();

        assertAll("confirmed intake",
                () -> assertSame(event, result,
                        "Should return the same (mutated) entity"),
                () -> assertEquals(IntakeEvent.Status.CONFIRMED, result.getStatus()),
                () -> assertEquals("prise faite", result.getNotes()),
                () -> assertNotNull(result.getConfirmedAt(),
                        "confirmedAt must be set"),
                () -> assertFalse(result.getConfirmedAt().isBefore(before),
                        "confirmedAt must not be before the call"),
                () -> assertFalse(result.getConfirmedAt().isAfter(after),
                        "confirmedAt must not be after the call returns")
        );
    }
}
