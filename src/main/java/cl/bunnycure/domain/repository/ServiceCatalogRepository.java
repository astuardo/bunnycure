package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.ServiceCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServiceCatalogRepository extends JpaRepository<ServiceCatalog, Long> {

    @Override
    @Query("SELECT s FROM ServiceCatalog s WHERE s.id = :id")
    java.util.Optional<ServiceCatalog> findById(@org.springframework.data.repository.query.Param("id") Long id);

    List<ServiceCatalog> findByActiveTrueOrderByDisplayOrderAsc();

    @Query("SELECT COALESCE(MAX(s.displayOrder), 0) FROM ServiceCatalog s")
    int findMaxDisplayOrder();
}
