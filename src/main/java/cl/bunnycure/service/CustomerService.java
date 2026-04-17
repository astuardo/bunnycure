package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.CustomerDto;
import cl.bunnycure.web.dto.CustomerSummary;
import cl.bunnycure.web.dto.CustomerLookupResponseDto;

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
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final GoogleWalletService googleWalletService;

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public List<Customer> search(String query) {
        return customerRepository.findByFullNameContainingIgnoreCase(query);
    }

    public Customer findById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
    }

    public Customer findByIdWithAppointments(Long id) {
        return customerRepository.findByIdWithAppointments(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
    }

    public Customer findByPublicId(String publicId) {
        return customerRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con identificador: " + publicId));
    }

    public Customer findByPublicIdWithAppointments(String publicId) {
        return customerRepository.findByPublicIdWithAppointments(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con identificador: " + publicId));
    }

    public List<CustomerSummary> findAllSummary() {
        return customerRepository.findAllWithAppointmentCount()
                .stream()
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]))
                .toList();
    }

    public List<CustomerSummary> searchSummary(String query) {
        // Para búsqueda usamos findByFullNameContaining y contamos en memoria
        return customerRepository.findByFullNameContainingIgnoreCase(query)
                .stream()
                .map(c -> new CustomerSummary(c,
                        customerRepository.countAppointmentsByCustomerId(c.getId())))
                .toList();
    }

    /**
     * Busca una clienta existente por teléfono para el formulario de reserva.
     * Retorna los datos de la clienta si existe, o una respuesta vacía si no.
     *
     * @param phone Número de teléfono en formato +56XXXXXXXXX
     * @return CustomerLookupResponseDto con datos de la clienta (found=true) o vacío (found=false)
     */
    public CustomerLookupResponseDto findByPhoneForLookup(String phone) {
        return customerRepository.findByPhone(phone)
                .map(customer -> CustomerLookupResponseDto.found(
                        customer.getId(),
                        customer.getFullName(),
                        customer.getPhone(),
                        customer.getEmail(),
                        customer.getGender(),
                        customer.getBirthDate(),
                        customer.getEmergencyPhone(),
                        customer.getHealthNotes()
                ))
                .orElseGet(CustomerLookupResponseDto::new);
    }

    /**
     * Finds a customer by phone number, trying multiple normalized formats.
     * Handles exact match, with/without '+' prefix, and Chilean local numbers (9 digits).
     * Used by WhatsApp webhook to match incoming messages to existing customers.
     */
    public java.util.Optional<Customer> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return java.util.Optional.empty();
        String digits = phone.replaceAll("[^0-9]", "");

        java.util.Optional<Customer> result = customerRepository.findByPhone(digits);
        if (result.isPresent()) return result;

        result = customerRepository.findByPhone("+" + digits);
        if (result.isPresent()) return result;

        if (digits.length() > 9) {
            String local = digits.substring(digits.length() - 9);
            result = customerRepository.findByPhone(local);
            if (result.isPresent()) return result;
        }
        return java.util.Optional.empty();
    }

    @Transactional
    public Customer create(@Valid @NotNull CustomerDto dto) {
        // Validar email solo si se proporciona
        if (dto.getEmail() != null && !dto.getEmail().isBlank() && customerRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un cliente con el email: " + dto.getEmail());
        }
        var customer = new Customer(dto.getFullName(), dto.getPhone(), normalizeNullable(dto.getEmail()));
        customer.setGender(normalizeGender(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizePhone(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        customer.setNotificationPreference(dto.getNotificationPreference() != null 
            ? dto.getNotificationPreference() 
            : cl.bunnycure.domain.enums.NotificationPreference.BOTH);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(@NotNull Long id, @Valid @NotNull CustomerDto dto) {
        var customer = findById(id);
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setEmail(normalizeNullable(dto.getEmail()));
        customer.setGender(normalizeGender(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizePhone(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        if (dto.getNotificationPreference() != null) {
            customer.setNotificationPreference(dto.getNotificationPreference());
        }
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateByPublicId(@NotNull String publicId, @Valid @NotNull CustomerDto dto) {
        var customer = findByPublicId(publicId);
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setEmail(normalizeNullable(dto.getEmail()));
        customer.setGender(normalizeGender(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizePhone(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        if (dto.getNotificationPreference() != null) {
            customer.setNotificationPreference(dto.getNotificationPreference());
        }
        return customerRepository.save(customer);
    }

    /**
     * Normaliza el género aceptando formatos cortos (M, F) o completos (MASCULINO, FEMENINO)
     */
    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toUpperCase();
        return switch (normalized) {
            case "M", "MALE" -> "MASCULINO";
            case "F", "FEMALE" -> "FEMENINO";
            default -> normalized; // Ya es MASCULINO o FEMENINO
        };
    }

    /**
     * Normaliza el teléfono de emergencia agregando +56 si solo tiene 9 dígitos
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        
        // Si tiene exactamente 9 dígitos (número chileno sin código país), agregar +56
        if (digits.length() == 9) {
            return "+56" + digits;
        }
        
        // Si tiene 11 dígitos y empieza con 56, agregar +
        if (digits.length() == 11 && digits.startsWith("56")) {
            return "+" + digits;
        }
        
        // Si ya tiene formato correcto o es de otro país, devolver como está
        return phone.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Transactional
    public void delete(Long id) {
        var customer = findById(id);
        customerRepository.delete(customer);
    }

    @Transactional
    public void deleteByPublicId(String publicId) {
        var customer = findByPublicId(publicId);
        customerRepository.delete(customer);
    }

    @Transactional
    public Customer adjustLoyaltyStamps(Long id, int delta) {
        var customer = findById(id);
        int current = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
        int nextValue = current + delta;
        
        if (nextValue < 0) nextValue = 0;
        
        customer.setLoyaltyStamps(nextValue);
        
        // Si el ajuste es positivo, también lo sumamos al total histórico (opcional, según lógica de negocio)
        if (delta > 0) {
            int total = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
            customer.setTotalCompletedVisits(total + delta);
        }
        
        Customer saved = customerRepository.save(customer);
        
        // Sincronizar con Google Wallet en segundo plano (opcionalmente podrías usar @Async)
        googleWalletService.updateCustomerStamps(saved);
        
        return saved;
    }
}