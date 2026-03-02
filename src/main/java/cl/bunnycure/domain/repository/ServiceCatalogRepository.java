package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.ServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    List<ServiceCatalog> findByActiveTrueOrderByDisplayOrderAsc();
}
