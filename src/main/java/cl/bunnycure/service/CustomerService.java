package cl.bunnycure.service;

import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.CustomerSearchText;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.CustomerDto;
import cl.bunnycure.web.dto.CustomerSummary;
import cl.bunnycure.web.dto.CustomerLookupResponseDto;
import cl.bunnycure.web.dto.LoyaltyCardPublicDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.beans.factory.annotation.Autowired;

@Service
@Validated
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final GoogleWalletService googleWalletService;
    private final LoyaltyRewardService loyaltyRewardService;
    private final PwaRedirectService pwaRedirectService;
    private final cl.bunnycure.domain.repository.TenantRepository tenantRepository;

    @Autowired
    public CustomerService(CustomerRepository customerRepository,
                           GoogleWalletService googleWalletService,
                           LoyaltyRewardService loyaltyRewardService,
                           PwaRedirectService pwaRedirectService,
                           @Autowired(required = false) cl.bunnycure.domain.repository.TenantRepository tenantRepository) {
        this.customerRepository = customerRepository;
        this.googleWalletService = googleWalletService;
        this.loyaltyRewardService = loyaltyRewardService;
        this.pwaRedirectService = pwaRedirectService;
        this.tenantRepository = tenantRepository;
    }

    public CustomerService(CustomerRepository customerRepository,
                           GoogleWalletService googleWalletService,
                           LoyaltyRewardService loyaltyRewardService,
                           PwaRedirectService pwaRedirectService) {
        this(customerRepository, googleWalletService, loyaltyRewardService, pwaRedirectService, null);
    }

    public CustomerService(CustomerRepository customerRepository,
                           GoogleWalletService googleWalletService,
                           LoyaltyRewardService loyaltyRewardService) {
        this(customerRepository, googleWalletService, loyaltyRewardService, null, null);
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public List<Customer> search(String query) {
        return customerRepository.findByFullNameContainingIgnoreCase(query);
    }

    @Transactional
    public Customer findById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
        if (customer.getPublicId() == null || customer.getPublicId().isBlank()) {
            customer.assignPublicIdIfMissing();
            customer = customerRepository.save(customer);
        }
        return customer;
    }

    @Transactional
    public Customer findByIdWithAppointments(Long id) {
        Customer customer = customerRepository.findByIdWithAppointments(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + id));
        if (customer.getPublicId() == null || customer.getPublicId().isBlank()) {
            customer.assignPublicIdIfMissing();
            customer = customerRepository.save(customer);
        }
        return customer;
    }

    public Customer findByPublicId(String publicId) {
        var customerOpt = customerRepository.findByPublicId(publicId);
        if (customerOpt.isEmpty() && isPublicOrAnonymous()) {
            customerRepository.findTenantIdByPublicId(publicId).ifPresent(cl.bunnycure.config.TenantContext::setCurrentTenantId);
            customerOpt = customerRepository.findByPublicId(publicId);
        }

        Customer customer = customerOpt
                .or(() -> {
                    try {
                        Long id = Long.parseLong(publicId);
                        return customerRepository.findById(id);
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con identificador: " + publicId));

        if (customer.getPublicId() == null || customer.getPublicId().isBlank()) {
            customer.assignPublicIdIfMissing();
            customer = customerRepository.save(customer);
        }
        return customer;
    }

    public Customer findByPublicIdWithAppointments(String publicId) {
        var customerOpt = customerRepository.findByPublicIdWithAppointments(publicId);
        if (customerOpt.isEmpty() && isPublicOrAnonymous()) {
            customerRepository.findTenantIdByPublicId(publicId).ifPresent(cl.bunnycure.config.TenantContext::setCurrentTenantId);
            customerOpt = customerRepository.findByPublicIdWithAppointments(publicId);
        }

        Customer customer = customerOpt
                .or(() -> {
                    try {
                        Long id = Long.parseLong(publicId);
                        return customerRepository.findByPublicIdWithAppointments(String.valueOf(id));
                    } catch (NumberFormatException e) {
                        return java.util.Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con identificador: " + publicId));

        if (customer.getPublicId() == null || customer.getPublicId().isBlank()) {
            customer.assignPublicIdIfMissing();
            customer = customerRepository.save(customer);
        }
        return customer;
    }

    private boolean isPublicOrAnonymous() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken;
    }

    public List<CustomerSummary> findAllSummary() {
        return customerRepository.findAllWithAppointmentCount()
                .stream()
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]))
                .toList();
    }

    public Page<CustomerSummary> findAllSummary(Pageable pageable) {
        return customerRepository.findAllWithAppointmentCount(pageable)
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]));
    }

    public List<CustomerSummary> searchSummary(String query) {
        if (query == null || query.isBlank()) {
            return findAllSummary();
        }
        String normalizedQuery = normalizeForSearch(query);
        String cleanQuery = normalizedQuery.replaceAll("[^0-9a-zA-Z]", "");

        return findAllSummary().stream()
                .filter(summary -> matchesSearch(summary, normalizedQuery, cleanQuery))
                .toList();
    }

    public Page<CustomerSummary> searchSummary(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return findAllSummary(pageable);
        }
        List<String> terms = CustomerSearchText.terms(query);
        if (terms.get(0).isEmpty()) {
            return findAllSummary(pageable);
        }
        return customerRepository.searchWithAppointmentCount(terms.get(0), terms.get(1), terms.get(2), terms.get(3), pageable)
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]));
    }

    public List<CustomerSummary> findSpecialistSummary(Long specialistId) {
        if (specialistId == null) {
            return findAllSummary();
        }
        return customerRepository.findSpecialistCustomers(specialistId)
                .stream()
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]))
                .toList();
    }

    public Page<CustomerSummary> findSpecialistSummary(Long specialistId, Pageable pageable) {
        if (specialistId == null) {
            return findAllSummary(pageable);
        }
        return customerRepository.findSpecialistCustomers(specialistId, pageable)
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]));
    }

    public List<CustomerSummary> searchSpecialistSummary(Long specialistId, String query) {
        if (specialistId == null) {
            return searchSummary(query);
        }
        if (query == null || query.isBlank()) {
            return findSpecialistSummary(specialistId);
        }
        String normalizedQuery = normalizeForSearch(query);
        String cleanQuery = normalizedQuery.replaceAll("[^0-9a-zA-Z]", "");

        return findSpecialistSummary(specialistId).stream()
                .filter(summary -> matchesSearch(summary, normalizedQuery, cleanQuery))
                .toList();
    }

    public Page<CustomerSummary> searchSpecialistSummary(Long specialistId, String query, Pageable pageable) {
        if (specialistId == null) {
            return searchSummary(query, pageable);
        }
        if (query == null || query.isBlank()) {
            return findSpecialistSummary(specialistId, pageable);
        }
        List<String> terms = CustomerSearchText.terms(query);
        if (terms.get(0).isEmpty()) {
            return findSpecialistSummary(specialistId, pageable);
        }
        return customerRepository.searchSpecialistCustomers(specialistId, terms.get(0), terms.get(1), terms.get(2), terms.get(3), pageable)
                .map(row -> new CustomerSummary((Customer) row[0], (Long) row[1]));
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        String normalized = java.text.Normalizer.normalize(input.trim().toLowerCase(), java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private boolean matchesSearch(CustomerSummary summary, String normalizedQuery, String cleanQuery) {
        if (normalizedQuery.isBlank()) return true;
        if (summary == null) return false;

        String name = normalizeForSearch(summary.getFullName());
        String phone = normalizeForSearch(summary.getPhone());
        String rut = normalizeForSearch(summary.getRut());
        String email = normalizeForSearch(summary.getEmail());

        if (name.contains(normalizedQuery) || phone.contains(normalizedQuery) || rut.contains(normalizedQuery) || email.contains(normalizedQuery)) {
            return true;
        }

        if (!cleanQuery.isBlank()) {
            String cleanPhone = phone.replaceAll("[^0-9]", "");
            String cleanRut = rut.replaceAll("[^0-9kK]", "").toLowerCase();
            if (cleanPhone.contains(cleanQuery) || cleanRut.contains(cleanQuery)) {
                return true;
            }
        }

        return false;
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
                        customer.getEmail()
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
        // Validar RUT
        String normalizedRut = normalizeRut(dto.getRut());
        if (normalizedRut == null || normalizedRut.isBlank()) {
            throw new IllegalArgumentException("El RUT es obligatorio");
        }
        if (customerRepository.existsByRut(normalizedRut)) {
            throw new IllegalArgumentException("Ya existe un cliente con el RUT: " + normalizedRut);
        }
        // Validar email solo si se proporciona
        if (dto.getEmail() != null && !dto.getEmail().isBlank() && customerRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Ya existe un cliente con el email: " + dto.getEmail());
        }
        var customer = new Customer(dto.getFullName(), dto.getPhone(), normalizeNullable(dto.getEmail()), normalizedRut);
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
        String normalizedRut = normalizeRut(dto.getRut());
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setRut(normalizedRut);
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
        String normalizedRut = normalizeRut(dto.getRut());
        customer.setFullName(dto.getFullName());
        customer.setPhone(dto.getPhone());
        customer.setRut(normalizedRut);
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

    private String normalizeRut(String rut) {
        if (rut == null || rut.isBlank()) {
            return null;
        }

        String cleaned = rut.trim().toUpperCase().replaceAll("[^0-9K]", "");
        if (cleaned.length() < 2) {
            return rut.trim();
        }

        String body = cleaned.substring(0, cleaned.length() - 1);
        String dv = cleaned.substring(cleaned.length() - 1);
        if (!body.matches("\\d+")) {
            return rut.trim().toUpperCase();
        }

        String formattedBody = body.replaceAll("(\\d)(?=(\\d{3})+$)", "$1.");
        return formattedBody + "-" + dv;
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
        
        // Si el ajuste es positivo o negativo, actualizar visitas completadas correspondientemente
        if (delta > 0) {
            int total = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
            customer.setTotalCompletedVisits(total + delta);
        } else if (delta < 0) {
            int total = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
            customer.setTotalCompletedVisits(Math.max(0, total + delta));
        }
        
        Customer saved = customerRepository.save(customer);
        
        // Sincronizar con Google Wallet y notificar actualización del pase.
        googleWalletService.updateCustomerStamps(saved, true);
        
        return saved;
    }

    /**
     * Sincroniza y recalcula las visitas completadas a partir de las citas reales en la base de datos.
     */
    @Transactional
    public Customer syncCustomerVisits(Long id) {
        var customer = findById(id);
        long completedCount = customerRepository.findByIdWithAppointments(id)
                .map(c -> c.getAppointments() != null
                        ? c.getAppointments().stream()
                                .filter(a -> a.getStatus() == cl.bunnycure.domain.enums.AppointmentStatus.COMPLETED)
                                .count()
                        : 0L)
                .orElse(0L);

        customer.setTotalCompletedVisits((int) completedCount);
        Customer saved = customerRepository.save(customer);
        googleWalletService.updateCustomerStamps(saved, true);
        return saved;
    }

    /**
     * Sincroniza y recalcula las visitas completadas de TODOS los clientes en la base de datos
     * basándose en sus citas en estado COMPLETED.
     */
    @Transactional
    public java.util.Map<String, Object> syncAllCustomerVisits() {
        List<Customer> allCustomers = customerRepository.findAll();
        int totalProcessed = allCustomers.size();
        int updatedCount = 0;

        for (Customer customer : allCustomers) {
            long completedCount = customerRepository.findByIdWithAppointments(customer.getId())
                    .map(c -> c.getAppointments() != null
                            ? c.getAppointments().stream()
                                    .filter(a -> a.getStatus() == cl.bunnycure.domain.enums.AppointmentStatus.COMPLETED)
                                    .count()
                            : 0L)
                    .orElse(0L);

            int currentVisits = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
            if (currentVisits != (int) completedCount) {
                customer.setTotalCompletedVisits((int) completedCount);
                customerRepository.save(customer);
                updatedCount++;
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalProcessed", totalProcessed);
        result.put("updatedCount", updatedCount);
        return result;
    }

    /**
     * Obtiene los datos consolidados de la tarjeta de fidelización digital pública para una clienta
     * basándose en su publicId.
     */
    public LoyaltyCardPublicDto getPublicLoyaltyCard(String publicId) {
        Customer customer = findByPublicId(publicId);

        int stamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
        int totalVisits = customer.getTotalCompletedVisits() != null ? customer.getTotalCompletedVisits() : 0;
        int maxStamps = 10;
        int clampedStamps = Math.min(Math.max(stamps, 0), maxStamps);
        int stampsRemaining = Math.max(0, maxStamps - clampedStamps);
        int progressPercent = (clampedStamps * 100) / maxStamps;

        String salonName = "BunnyCure";
        String salonInstagram = "@bunnycure.cl";
        String salonWhatsapp = "+56927978078";
        String tenantSlug = "bunnycure";
        String logoUrl = null;
        String primaryColor = "#d48b70";

        if (tenantRepository != null && customer.getTenantId() != null) {
            var tenantOpt = tenantRepository.findById(customer.getTenantId());
            if (tenantOpt.isPresent()) {
                var tenant = tenantOpt.get();
                if (tenant.getName() != null && !tenant.getName().isBlank()) {
                    salonName = tenant.getName();
                }
                if (tenant.getPhone() != null && !tenant.getPhone().isBlank()) {
                    salonWhatsapp = tenant.getPhone();
                }
                if (tenant.getSlug() != null && !tenant.getSlug().isBlank()) {
                    tenantSlug = tenant.getSlug();
                }
                if (tenant.getLogoUrl() != null && !tenant.getLogoUrl().isBlank()) {
                    logoUrl = tenant.getLogoUrl();
                }
                if (tenant.getPrimaryColor() != null && !tenant.getPrimaryColor().isBlank()) {
                    primaryColor = tenant.getPrimaryColor();
                }
            }
        }

        int rewardIndex = customer.getCurrentRewardIndex() != null ? customer.getCurrentRewardIndex() : 0;
        var reward = loyaltyRewardService.getRewardAt(rewardIndex);
        String rewardName = (reward != null && reward.getName() != null && !reward.getName().isBlank())
                ? reward.getName()
                : "Premio Especial " + salonName;

        boolean rewardAvailable = clampedStamps >= maxStamps;
        String statusMessage = rewardAvailable
                ? "¡Felicidades! Tienes tu premio listo para canjear en tu próxima visita."
                : (stampsRemaining == 1
                    ? "¡Te falta solo 1 visita para ganar: " + rewardName + "!"
                    : "Te faltan " + stampsRemaining + " visitas para ganar: " + rewardName);

        String googleWalletUrl = null;
        try {
            googleWalletUrl = googleWalletService.createWalletLink(customer);
        } catch (Exception e) {
            // Google Wallet es opcional, no interrumpe la carga de la tarjeta web
        }

        String frontendBase = pwaRedirectService != null ? pwaRedirectService.getFrontendBaseUrl() : "https://app.bunnycure.cl";
        String qrValue = frontendBase + "/mi-tarjeta/" + customer.getPublicId();

        return LoyaltyCardPublicDto.builder()
                .publicId(customer.getPublicId())
                .customerName(customer.getFullName())
                .firstName(customer.getFirstName())
                .phone(maskPhone(customer.getPhone()))
                .loyaltyStamps(clampedStamps)
                .totalCompletedVisits(totalVisits)
                .maxStamps(maxStamps)
                .stampsRemaining(stampsRemaining)
                .progressPercent(progressPercent)
                .currentRewardName(rewardName)
                .rewardAvailable(rewardAvailable)
                .rewardStatusMessage(statusMessage)
                .qrValue(qrValue)
                .googleWalletUrl(googleWalletUrl)
                .salonName(salonName)
                .salonInstagram(salonInstagram)
                .salonWhatsapp(salonWhatsapp)
                .tenantSlug(tenantSlug)
                .logoUrl(logoUrl)
                .primaryColor(primaryColor)
                .build();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        if (phone.length() <= 4) return phone;
        int visibleDigits = 4;
        String prefix = phone.substring(0, Math.min(phone.length() - visibleDigits, 4));
        String suffix = phone.substring(phone.length() - visibleDigits);
        return prefix + " •••• " + suffix;
    }
}
