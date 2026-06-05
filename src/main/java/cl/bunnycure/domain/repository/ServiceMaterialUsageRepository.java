package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.ServiceMaterialUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceMaterialUsageRepository extends JpaRepository<ServiceMaterialUsage, Long> {
    List<ServiceMaterialUsage> findByServiceId(Long serviceId);
}
