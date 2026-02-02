package com.pillpall.med_application.dispenser;

import com.pillpall.med_application.intakes.IntakeEvent;
import com.pillpall.med_application.intakes.IntakeEventRepository;
import com.pillpall.med_application.prescriptions.Prescription;
import com.pillpall.med_application.prescriptions.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service pour gérer la communication avec les distributeurs ESP32
 * Version mise à jour pour correspondre au schéma IntakeEvent existant
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DispenserService {

    private final DispenserDeviceRepository deviceRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final IntakeEventRepository intakeEventRepository;
    private final RestTemplate restTemplate;

    private static final String DISPENSER_PRESCRIPTION_ENDPOINT = "/api/v1/dispenser/prescriptions";
    private static final int CONNECTION_TIMEOUT = 5000; // 5 secondes

    /**
     * Synchroniser les prescriptions d'un patient avec son distributeur
     */
    @Transactional
    public boolean syncPrescriptionsToDispenser(Long patientId) {
        log.info("Synchronisation des prescriptions pour le patient {}", patientId);

        // Trouver le distributeur actif du patient
        DispenserDevice device = deviceRepository.findActiveByPatientId(patientId)
                .orElseThrow(() -> new IllegalStateException("Aucun distributeur actif trouvé pour ce patient"));

        // Récupérer les prescriptions actives du patient
        LocalDate today = LocalDate.now();
        List<Prescription> activePrescriptions = prescriptionRepository
                .findByPatientIdAndDateRange(patientId, today, today.plusMonths(1));

        if (activePrescriptions.isEmpty()) {
            log.warn("Aucune prescription active pour le patient {}", patientId);
            return false;
        }

        // Construire le DTO pour l'ESP32
        DispenserPrescriptionDTO dto = buildDispenserDTO(patientId, activePrescriptions);

        // Envoyer au distributeur
        return sendPrescriptionsToDevice(device, dto);
    }

    /**
     * Synchroniser une prescription spécifique
     */
    @Transactional
    public boolean syncSinglePrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription introuvable"));

        Long patientId = prescription.getPatient().getId();

        DispenserDevice device = deviceRepository.findActiveByPatientId(patientId)
                .orElseThrow(() -> new IllegalStateException("Aucun distributeur actif pour ce patient"));

        // Créer un DTO avec uniquement cette prescription
        DispenserPrescriptionDTO dto = buildDispenserDTO(patientId, List.of(prescription));

        return sendPrescriptionsToDevice(device, dto);
    }

    /**
     * Construire le DTO au format attendu par l'ESP32
     */
    private DispenserPrescriptionDTO buildDispenserDTO(Long patientId, List<Prescription> prescriptions) {
        List<DispenserPrescriptionDTO.PrescriptionSchedule> schedules = prescriptions.stream()
                .map(p -> {
                    List<DispenserPrescriptionDTO.DoseSchedule> doseTimes = p.getDoseTimes().stream()
                            .map(dt -> DispenserPrescriptionDTO.DoseSchedule.builder()
                                    .hour(dt.getHour())
                                    .minute(dt.getMinute())
                                    .compartment(1) // À adapter selon votre logique multi-compartiments
                                    .build())
                            .collect(Collectors.toList());

                    return DispenserPrescriptionDTO.PrescriptionSchedule.builder()
                            .prescriptionId(p.getId())
                            .medicationName(p.getMedicationName())
                            .dosage(p.getDosage())
                            .startDate(p.getStartDate().toString())
                            .endDate(p.getEndDate().toString())
                            .doseTimes(doseTimes)
                            .build();
                })
                .collect(Collectors.toList());

        return DispenserPrescriptionDTO.builder()
                .patientId(patientId)
                .prescriptions(schedules)
                .build();
    }

    /**
     * Envoyer les données au distributeur ESP32 via HTTP
     */
    private boolean sendPrescriptionsToDevice(DispenserDevice device, DispenserPrescriptionDTO dto) {
        String url = String.format("http://%s:%d%s",
                device.getIpAddress(),
                device.getPort(),
                DISPENSER_PRESCRIPTION_ENDPOINT);

        try {
            log.info("Envoi des prescriptions au distributeur: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<DispenserPrescriptionDTO> request = new HttpEntity<>(dto, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET, // L'ESP32 utilise GET avec body (non standard mais fonctionnel)
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Synchronisation réussie avec le distributeur {}", device.getDeviceId());
                device.setLastSync(Instant.now());
                deviceRepository.save(device);
                return true;
            } else {
                log.error("Échec de la synchronisation: HTTP {}", response.getStatusCode());
                return false;
            }

        } catch (ResourceAccessException e) {
            log.error("Impossible de contacter le distributeur {}: {}", device.getDeviceId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Erreur lors de la synchronisation avec le distributeur", e);
            return false;
        }
    }

    /**
     * Traiter un statut reçu du distributeur
     * MISE À JOUR: Utilise CONFIRMED/MISSED au lieu de TAKEN/MISSED
     */
    @Transactional
    public void processDispenserStatus(DispenserStatusDTO statusDTO) {
        log.info("Traitement du statut du distributeur: {}", statusDTO);

        // Vérifier que le dispositif existe
        DispenserDevice device = deviceRepository.findByDeviceId(statusDTO.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Distributeur inconnu: " + statusDTO.getDeviceId()));

        // Mettre à jour le heartbeat
        device.setLastHeartbeat(Instant.now());
        deviceRepository.save(device);

        // Trouver l'événement d'intake correspondant
        Instant scheduledTime = Instant.parse(statusDTO.getScheduledTime());

        List<IntakeEvent> events = intakeEventRepository.findByPrescriptionIdAndScheduledAt(
                statusDTO.getPrescriptionId(),
                scheduledTime
        );

        if (events.isEmpty()) {
            log.warn("Aucun événement d'intake trouvé pour prescription {} à {}",
                    statusDTO.getPrescriptionId(), scheduledTime);
            return;
        }

        IntakeEvent event = events.get(0);

        // Mettre à jour le statut selon le rapport du distributeur
        switch (statusDTO.getStatus()) {
            case "TAKEN":
                // IMPORTANT: Votre schéma utilise CONFIRMED, pas TAKEN
                event.setStatus(IntakeEvent.Status.CONFIRMED);
                event.setConfirmedAt(Instant.parse(statusDTO.getActualTime()));
                log.info("Médicament pris avec succès pour l'événement {}", event.getId());
                break;

            case "MISSED":
                event.setStatus(IntakeEvent.Status.MISSED);
                log.warn("Médicament manqué pour l'événement {}", event.getId());
                break;

            case "ALERT_SENT":
            case "SMS_SENT":
            case "CALL_MADE":
                // Le distributeur a envoyé une alerte
                // Vous pouvez ajouter des notes pour tracer les alertes
                String existingNotes = event.getNotes() != null ? event.getNotes() : "";
                String alertNote = String.format("\n[%s] Alerte niveau %d: %s",
                        Instant.now(), statusDTO.getAlertLevel(), statusDTO.getStatus());
                event.setNotes(existingNotes + alertNote);

                log.info("Alerte de niveau {} envoyée pour l'événement {}",
                        statusDTO.getAlertLevel(), event.getId());
                break;
        }

        intakeEventRepository.save(event);
    }

    /**
     * Enregistrer un nouveau distributeur
     */
    @Transactional
    public DispenserDevice registerDevice(String deviceId, String ipAddress, Integer port, Long patientId) {
        // Vérifier qu'un seul distributeur actif par patient
        deviceRepository.findByPatientId(patientId).ifPresent(existing -> {
            existing.setActive(false);
            deviceRepository.save(existing);
        });

        DispenserDevice device = DispenserDevice.builder()
                .deviceId(deviceId)
                .ipAddress(ipAddress)
                .port(port != null ? port : 80)
                .patient(new com.pillpall.med_application.users.PatientProfile() {{ setId(patientId); }})
                .active(true)
                .lastHeartbeat(Instant.now())
                .build();

        return deviceRepository.save(device);
    }

    /**
     * Vérifier l'état de connexion d'un distributeur
     */
    public boolean checkDeviceStatus(String deviceId) {
        DispenserDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Distributeur introuvable"));

        String url = String.format("http://%s:%d/api/v1/dispenser/health",
                device.getIpAddress(), device.getPort());

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                device.setLastHeartbeat(Instant.now());
                deviceRepository.save(device);
                return true;
            }
        } catch (Exception e) {
            log.error("Distributeur {} non disponible", deviceId);
        }

        return false;
    }
}
