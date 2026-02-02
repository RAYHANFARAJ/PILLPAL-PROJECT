package com.pillpall.med_application.intakes;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface IntakeEventRepository extends JpaRepository<IntakeEvent, Long> {

    List<IntakeEvent> findByStatusAndScheduledAtBetween(IntakeEvent.Status status, Instant from, Instant to);

    List<IntakeEvent> findByStatusAndScheduledAtBefore(IntakeEvent.Status status, Instant before);

    List<IntakeEvent> findByPrescriptionPatientId(Long patientId);

    List<IntakeEvent> findByPrescriptionPatientIdAndStatus(Long patientId, IntakeEvent.Status status);

    List<IntakeEvent> findByPrescriptionPatientIdAndScheduledAtBetween(Long patientId, Instant todayStart, Instant tomorrowStart);

    // ========== NOUVELLES MÉTHODES POUR L'INTÉGRATION DISTRIBUTEUR ==========

    /**
     * Trouver les événements d'intake par prescription et heure planifiée
     * Utilisé pour matcher les statuts reçus du distributeur ESP32
     */
    @Query("SELECT i FROM IntakeEvent i WHERE i.prescription.id = :prescriptionId AND i.scheduledAt = :scheduledAt")
    List<IntakeEvent> findByPrescriptionIdAndScheduledAt(
            @Param("prescriptionId") Long prescriptionId,
            @Param("scheduledAt") Instant scheduledAt
    );

    /**
     * Supprimer les événements liés à une prescription
     * Utilisé lors de la suppression d'une prescription
     */
    @Modifying
    @Query("DELETE FROM IntakeEvent i WHERE i.prescription.id = :prescriptionId")
    void deleteByPrescriptionId(@Param("prescriptionId") Long prescriptionId);
}
