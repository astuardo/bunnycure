package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.MarketingTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketingTemplateRepository extends JpaRepository<MarketingTemplateEntity, Long> {

    @Override
    @org.springframework.data.jpa.repository.Query("SELECT m FROM MarketingTemplateEntity m WHERE m.id = :id")
    Optional<MarketingTemplateEntity> findById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<MarketingTemplateEntity> findByName(String name);

    Optional<MarketingTemplateEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<MarketingTemplateEntity> findAllByOrderByCreatedAtDesc();
}
