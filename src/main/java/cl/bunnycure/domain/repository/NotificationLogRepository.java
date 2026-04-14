package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByAppointmentId(Long appointmentId);
    List<NotificationLog> findByCustomerId(Long customerId);
    NotificationLog findByWamid(String wamid);
}
