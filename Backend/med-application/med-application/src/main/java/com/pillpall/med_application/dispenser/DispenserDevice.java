package com.pillpall.med_application.dispenser;

import com.pillpall.med_application.users.PatientProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entité représentant un distributeur ESP32 assigné à un patient
 */
@Entity
@Table(name = "dispenser_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispenserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deviceId; // Identifiant unique du distributeur (MAC address ou UUID)

    @Column(nullable = false)
    private String ipAddress; // Adresse IP locale du distributeur

    @Column(nullable = false)
    private Integer port; // Port du serveur HTTP (généralement 80)

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientProfile patient;

    @Column(nullable = false)
    private boolean active; // Si le distributeur est actif

    @Column(name = "last_sync")
    private Instant lastSync; // Dernière synchronisation réussie

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat; // Dernier signal de vie du distributeur

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
