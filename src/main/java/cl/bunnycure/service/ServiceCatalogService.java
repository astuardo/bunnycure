package cl.bunnycure.service;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ServiceCatalogService {

    private final ServiceCatalogRepository repository;

    public ServiceCatalogService(ServiceCatalogRepository repository) {
        this.repository = repository;
    }

    public List<ServiceCatalog> findAllActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<ServiceCatalog> findAll() {
        return repository.findAll(
                org.springframework.data.domain.Sort.by("displayOrder")
        );
    }

    public ServiceCatalog findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
    }

    @Transactional
    public ServiceCatalog save(ServiceCatalogDto dto) {
        var s = (dto.getId() != null) ? findById(dto.getId()) : new ServiceCatalog();
        s.setName(dto.getName());
        s.setDurationMinutes(dto.getDurationMinutes());
        s.setPrice(dto.getPrice());
        s.setDescription(dto.getDescription());
        s.setActive(dto.getActive() != null ? dto.getActive() : true);
        s.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 0);
        return repository.save(s);
    }

    @Transactional
    public void toggleActive(Long id) {
        var s = findById(id);
        s.setActive(!s.getActive());
        repository.save(s);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}