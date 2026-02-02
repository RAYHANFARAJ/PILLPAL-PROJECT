package com.pillpall.med_application.dispenser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller pour gérer la communication avec les distributeurs ESP32
 */
@RestController
@RequestMapping("/api/dispenser")
@RequiredArgsConstructor
@Slf4j
public class DispenserController {

    private final DispenserService dispenserService;
    private final DispenserDeviceRepository deviceRepository;

    /**
     * Endpoint pour recevoir les statuts du distributeur ESP32
     * Appelé par l'ESP32 via POST /api/v1/dispenser/status
     */
    @PostMapping("/status")
    public ResponseEntity<?> receiveStatus(@RequestBody DispenserStatusDTO statusDTO) {
        log.info("Réception du statut du distributeur: {}", statusDTO.getDeviceId());

        try {
            dispenserService.processDispenserStatus(statusDTO);
            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "message", "Status processed successfully"
            ));
        } catch (Exception e) {
            log.error("Erreur lors du traitement du statut", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Synchroniser manuellement les prescriptions d'un patient avec son distributeur
     * Appelé par le médecin ou automatiquement après création/modification d'une prescription
     */
    @PostMapping("/sync/{patientId}")
    public ResponseEntity<?> syncPrescriptions(
            @AuthenticationPrincipal String email,
            @PathVariable Long patientId
    ) {
        log.info("Demande de synchronisation pour le patient {}", patientId);

        try {
            boolean success = dispenserService.syncPrescriptionsToDispenser(patientId);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Prescriptions synchronisées avec le distributeur"
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                        "status", "error",
                        "message", "Échec de la synchronisation"
                ));
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Enregistrer un nouveau distributeur pour un patient
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(
            @AuthenticationPrincipal String email,
            @RequestBody RegisterDeviceRequest request
    ) {
        log.info("Enregistrement d'un nouveau distributeur: {}", request.getDeviceId());

        try {
            DispenserDevice device = dispenserService.registerDevice(
                    request.getDeviceId(),
                    request.getIpAddress(),
                    request.getPort(),
                    request.getPatientId()
            );

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Distributeur enregistré avec succès",
                    "deviceId", device.getDeviceId()
            ));
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement du distributeur", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Vérifier l'état de connexion d'un distributeur
     */
    @GetMapping("/status/{deviceId}")
    public ResponseEntity<?> checkDeviceStatus(@PathVariable String deviceId) {
        boolean online = dispenserService.checkDeviceStatus(deviceId);
        
        DispenserDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Distributeur introuvable"));

        return ResponseEntity.ok(Map.of(
                "deviceId", deviceId,
                "online", online,
                "lastHeartbeat", device.getLastHeartbeat(),
                "lastSync", device.getLastSync()
        ));
    }

    /**
     * Obtenir les informations d'un distributeur par patient
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<?> getDeviceByPatient(@PathVariable Long patientId) {
        return deviceRepository.findByPatientId(patientId)
                .map(device -> ResponseEntity.ok(Map.of(
                        "deviceId", device.getDeviceId(),
                        "ipAddress", device.getIpAddress(),
                        "port", device.getPort(),
                        "active", device.isActive(),
                        "lastSync", device.getLastSync(),
                        "lastHeartbeat", device.getLastHeartbeat()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Désactiver un distributeur
     */
    @PutMapping("/{deviceId}/deactivate")
    public ResponseEntity<?> deactivateDevice(@PathVariable String deviceId) {
        DispenserDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Distributeur introuvable"));

        device.setActive(false);
        deviceRepository.save(device);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Distributeur désactivé"
        ));
    }

    // DTO pour l'enregistrement d'un dispositif
    @lombok.Data
    public static class RegisterDeviceRequest {
        private String deviceId;
        private String ipAddress;
        private Integer port;
        private Long patientId;
    }
}
