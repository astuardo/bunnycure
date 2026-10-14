package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.CustomerServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerServiceRecordRepository extends JpaRepository<CustomerServiceRecord, Long> {

    @Override
    @org.springframework.data.jpa.repository.Query("SELECT r FROM CustomerServiceRecord r WHERE r.id = :id")
    Optional<CustomerServiceRecord> findById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<CustomerServiceRecord> findBySourceMessageId(String sourceMessageId);

    List<CustomerServiceRecord> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    List<CustomerServiceRecord> findTop3ByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
