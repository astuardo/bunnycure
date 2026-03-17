package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.CustomerDto;
import cl.bunnycure.web.dto.CustomerSummary;
import cl.bunnycure.web.dto.CustomerLookupResponseDto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

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
    public Customer create(CustomerDto dto) {
        if (customerRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un cliente con el email: " + dto.getEmail());
        }
        var customer = new Customer(dto.getFullName(), dto.getPhone(), dto.getEmail());
        customer.setGender(normalizeNullable(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizeNullable(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, CustomerDto dto) {
        var customer = findById(id);
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setGender(normalizeNullable(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizeNullable(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        // Email no se actualiza para evitar duplicados silenciosos
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateByPublicId(String publicId, CustomerDto dto) {
        var customer = findByPublicId(publicId);
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setGender(normalizeNullable(dto.getGender()));
        customer.setBirthDate(dto.getBirthDate());
        customer.setEmergencyPhone(normalizeNullable(dto.getEmergencyPhone()));
        customer.setHealthNotes(normalizeNullable(dto.getHealthNotes()));
        customer.setNotes(dto.getNotes());
        return customerRepository.save(customer);
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
}