package com.pillpall.med_application.dispenser;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO pour recevoir les statuts d'intake du distributeur ESP32
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispenserStatusDTO {

    @JsonProperty("prescription_id")
    private Long prescriptionId;

    @JsonProperty("patient_id")
    private Long patientId;

    @JsonProperty("scheduled_time")
    private String scheduledTime; // Format ISO-8601: "2025-02-02T08:00:00Z"

    @JsonProperty("actual_time")
    private String actualTime; // Heure réelle de prise

    @JsonProperty("status")
    private String status; // "TAKEN", "MISSED", "ALERT_SENT", "SMS_SENT", "CALL_MADE"

    @JsonProperty("sensor_detected")
    private boolean sensorDetected; // true si le capteur ultrasonique a détecté la prise

    @JsonProperty("drawer_opened")
    private boolean drawerOpened;

    @JsonProperty("compartment")
    private Integer compartment;

    @JsonProperty("alert_level")
    private Integer alertLevel; // 0: aucune alerte, 1: OLED, 2: SMS, 3: Appel

    @JsonProperty("device_id")
    private String deviceId; // Identifiant unique du distributeur ESP32
}
