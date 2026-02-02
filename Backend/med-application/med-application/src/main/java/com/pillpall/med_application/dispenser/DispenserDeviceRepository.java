package com.pillpall.med_application.dispenser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DispenserDeviceRepository extends JpaRepository<DispenserDevice, Long> {

    Optional<DispenserDevice> findByDeviceId(String deviceId);

    Optional<DispenserDevice> findByPatientId(Long patientId);

    @Query("SELECT d FROM DispenserDevice d WHERE d.patient.id = :patientId AND d.active = true")
    Optional<DispenserDevice> findActiveByPatientId(@Param("patientId") Long patientId);

    boolean existsByDeviceId(String deviceId);

    boolean existsByPatientId(Long patientId);
}
