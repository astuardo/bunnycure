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
import java.util.List;

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
        return repository.save(s);
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