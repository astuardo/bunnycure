package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.enums.OutboxStatus;
import cl.bunnycure.domain.model.WhatsAppOutboxMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WhatsAppOutboxMessageRepository extends JpaRepository<WhatsAppOutboxMessage, Long> {

    Page<WhatsAppOutboxMessage> findByStatusInOrderByCreatedAtDesc(Collection<OutboxStatus> statuses, Pageable pageable);

    Page<WhatsAppOutboxMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatusIn(Collection<OutboxStatus> statuses);

    List<WhatsAppOutboxMessage> findByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses);

    @Modifying
    @Query("UPDATE WhatsAppOutboxMessage m SET m.status = :targetStatus WHERE m.status IN :sourceStatuses")
    int updateStatusForStatuses(
            @Param("targetStatus") OutboxStatus targetStatus,
            @Param("sourceStatuses") Collection<OutboxStatus> sourceStatuses
    );
}
