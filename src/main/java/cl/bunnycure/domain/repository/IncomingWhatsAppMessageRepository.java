package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.IncomingWhatsAppMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncomingWhatsAppMessageRepository extends JpaRepository<IncomingWhatsAppMessage, Long> {

    Page<IncomingWhatsAppMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<IncomingWhatsAppMessage> findByIsReadFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<IncomingWhatsAppMessage> findByIsReadTrueOrderByCreatedAtDesc(Pageable pageable);

    long countByIsReadFalse();

    Optional<IncomingWhatsAppMessage> findByWamid(String wamid);

    List<IncomingWhatsAppMessage> findTop10ByOrderByCreatedAtDesc();

    @Modifying
    @Query("UPDATE IncomingWhatsAppMessage m SET m.isRead = true WHERE m.isRead = false")
    int markAllAsRead();

    @Modifying
    @Query("UPDATE IncomingWhatsAppMessage m SET m.isRead = true WHERE (m.fromPhone = :phone OR m.fromPhone = :cleanPhone) AND m.isRead = false")
    int markByPhoneAsRead(@org.springframework.data.repository.query.Param("phone") String phone, @org.springframework.data.repository.query.Param("cleanPhone") String cleanPhone);
}
