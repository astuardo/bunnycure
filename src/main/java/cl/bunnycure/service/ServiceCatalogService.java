package cl.bunnycure.service;

import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.ServiceCatalogDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Validated
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ServiceCatalogService {

    public enum DeleteOutcome {
        DELETED,
        DEACTIVATED_REFERENCED
    }

    private final ServiceCatalogRepository repository;
    private final AppointmentRepository appointmentRepository;
    private final BookingRequestRepository bookingRequestRepository;

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

    public List<ServiceCatalog> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Long> normalizedIds = ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        List<ServiceCatalog> found = repository.findAllById(normalizedIds);
        Map<Long, ServiceCatalog> byId = new LinkedHashMap<>();
        found.forEach(service -> byId.put(service.getId(), service));

        if (byId.size() != normalizedIds.size()) {
            List<Long> missing = normalizedIds.stream()
                    .filter(id -> !byId.containsKey(id))
                    .toList();
            throw new ResourceNotFoundException("Servicios no encontrados: " + missing);
        }

        List<ServiceCatalog> ordered = new ArrayList<>();
        for (Long id : normalizedIds) {
            ordered.add(byId.get(id));
        }
        return ordered;
    }

    @Transactional
    public ServiceCatalog save(@Valid @NotNull ServiceCatalogDto dto) {
        ServiceCatalog s;
        if (dto.getId() != null) {
            s = findById(dto.getId());
        } else {
            s = new ServiceCatalog();
            s.setActive(true);
            // ✅ displayOrder = último + 1, o usa el valor del form si lo ingresaron
            int nextOrder = dto.getDisplayOrder() != null && dto.getDisplayOrder() > 0
                    ? dto.getDisplayOrder()
                    : repository.findMaxDisplayOrder() + 1;
            s.setDisplayOrder(nextOrder);
        }
        s.setName(dto.getName());
        s.setDurationMinutes(dto.getDurationMinutes());
        s.setPrice(dto.getPrice());
        s.setDescription(dto.getDescription());
        if (dto.getActive() != null) s.setActive(dto.getActive());
        if (dto.getDisplayOrder() != null && dto.getId() != null) s.setDisplayOrder(dto.getDisplayOrder());
        applyCompatibleServices(s, dto.getCompatibleServiceIds());
        return repository.save(s);
    }

    private void applyCompatibleServices(ServiceCatalog service, List<Long> compatibleServiceIds) {
        if (compatibleServiceIds == null) {
            service.setCompatibleServices(new LinkedHashSet<>());
            return;
        }

        List<Long> normalizedIds = compatibleServiceIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (service.getId() != null) {
            normalizedIds = normalizedIds.stream()
                    .filter(id -> !id.equals(service.getId()))
                    .toList();
        }

        List<ServiceCatalog> compatibleServices = findByIds(normalizedIds);
        service.setCompatibleServices(new LinkedHashSet<>(compatibleServices));
    }

    @Transactional
    public void toggleActive(Long id) {
        var s = findById(id);
        s.setActive(!s.getActive());
        repository.save(s);
    }

    @Transactional
    public DeleteOutcome delete(Long id) {
        ServiceCatalog service = findById(id);
        long linkedAppointments = appointmentRepository.countByServiceId(id);
        long linkedBookingRequests = bookingRequestRepository.countByServiceId(id);

        if (linkedAppointments > 0 || linkedBookingRequests > 0) {
            service.setActive(false);
            repository.save(service);
            return DeleteOutcome.DEACTIVATED_REFERENCED;
        }

        repository.deleteById(id);
        return DeleteOutcome.DELETED;
    }
}
