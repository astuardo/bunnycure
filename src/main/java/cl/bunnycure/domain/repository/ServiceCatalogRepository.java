package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.ServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    List<ServiceCatalog> findByActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(s.displayOrder), 0) FROM ServiceCatalog s")
    int findMaxDisplayOrder();
}
