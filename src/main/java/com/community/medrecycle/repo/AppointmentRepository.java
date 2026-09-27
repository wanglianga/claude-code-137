package com.community.medrecycle.repo;

import com.community.medrecycle.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    List<Appointment> findByResidentIdOrderByIdDesc(Long residentId);

    List<Appointment> findByCommunityPointIdAndStatus(Long communityPointId, Appointment.Status status);
}
