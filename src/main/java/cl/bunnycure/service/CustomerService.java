package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.CustomerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cl.bunnycure.web.dto.CustomerSummary;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

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

    @Transactional
    public Customer create(CustomerDto dto) {
        if (customerRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un cliente con el email: " + dto.getEmail());
        }
        var customer = new Customer(dto.getFullName(), dto.getPhone(), dto.getEmail());
        customer.setNotes(dto.getNotes());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, CustomerDto dto) {
        var customer = findById(id);
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setNotes(dto.getNotes());
        // Email no se actualiza para evitar duplicados silenciosos
        return customerRepository.save(customer);
    }

    @Transactional
    public void delete(Long id) {
        var customer = findById(id);
        customerRepository.delete(customer);
    }
}