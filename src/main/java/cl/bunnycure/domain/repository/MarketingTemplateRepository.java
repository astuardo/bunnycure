package cl.bunnycure.domain.repository;

import cl.bunnycure.domain.model.MarketingTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketingTemplateRepository extends JpaRepository<MarketingTemplateEntity, Long> {

    Optional<MarketingTemplateEntity> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<MarketingTemplateEntity> findAllByOrderByCreatedAtDesc();
}
