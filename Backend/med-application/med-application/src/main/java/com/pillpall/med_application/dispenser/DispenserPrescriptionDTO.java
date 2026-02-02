package com.pillpall.med_application.dispenser;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO pour envoyer les prescriptions au distributeur ESP32
 * Format JSON adapté au protocole HTTP REST du distributeur
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispenserPrescriptionDTO {

    @JsonProperty("patient_id")
    private Long patientId;

    @JsonProperty("prescriptions")
    private List<PrescriptionSchedule> prescriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrescriptionSchedule {
        
        @JsonProperty("prescription_id")
        private Long prescriptionId;
        
        @JsonProperty("medication_name")
        private String medicationName;
        
        @JsonProperty("dosage")
        private String dosage;
        
        @JsonProperty("start_date")
        private String startDate; // Format: "YYYY-MM-DD"
        
        @JsonProperty("end_date")
        private String endDate; // Format: "YYYY-MM-DD"
        
        @JsonProperty("dose_times")
        private List<DoseSchedule> doseTimes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DoseSchedule {
        
        @JsonProperty("hour")
        private int hour; // 0-23
        
        @JsonProperty("minute")
        private int minute; // 0-59
        
        @JsonProperty("compartment")
        private int compartment; // Numéro du compartiment (optionnel, pour plusieurs médicaments)
    }
}
