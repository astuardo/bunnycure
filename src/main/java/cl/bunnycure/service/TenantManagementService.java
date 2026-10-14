package cl.bunnycure.service;

import cl.bunnycure.domain.model.Tenant;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.TenantRepository;
import cl.bunnycure.domain.repository.UserRepository;
import cl.bunnycure.exception.ResourceNotFoundException;
import cl.bunnycure.web.dto.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantManagementService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantExecutionService tenantExecutionService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<TenantResponseDto> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        return tenants.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TenantResponseDto getTenantById(Long id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salón no encontrado con ID: " + id));
        return mapToResponseDto(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantSummaryDto> getTenantSummaries() {
        return tenantRepository.findByActiveTrue().stream()
                .map(t -> TenantSummaryDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .slug(t.getSlug())
                        .customDomain(t.getCustomDomain())
                        .active(t.isActive())
                        .planTier(t.getPlanTier())
                        .primaryColor(t.getPrimaryColor())
                        .logoUrl(t.getLogoUrl())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public TenantResponseDto createTenant(TenantCreateRequestDto dto) {
        String slug = dto.getSlug().trim().toLowerCase();

        if (tenantRepository.existsBySlugIgnoreCase(slug)) {
            throw new IllegalArgumentException("Ya existe un salón registrado con el slug: " + slug);
        }

        String customDomain = dto.getCustomDomain() != null && !dto.getCustomDomain().isBlank()
                ? dto.getCustomDomain().trim().toLowerCase()
                : null;

        if (customDomain != null && tenantRepository.existsByCustomDomainIgnoreCase(customDomain)) {
            throw new IllegalArgumentException("Ya existe un salón registrado con el dominio: " + customDomain);
        }

        Tenant tenant = Tenant.builder()
                .name(dto.getName().trim())
                .slug(slug)
                .customDomain(customDomain)
                .rut(dto.getRut())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .logoUrl(dto.getLogoUrl())
                .primaryColor(dto.getPrimaryColor() != null && !dto.getPrimaryColor().isBlank() ? dto.getPrimaryColor() : "#d48b70")
                .planTier(dto.getPlanTier() != null && !dto.getPlanTier().isBlank() ? dto.getPlanTier().toUpperCase() : "STANDARD")
                .whatsappPhoneId(dto.getWhatsappPhoneId())
                .whatsappToken(dto.getWhatsappToken())
                .active(true)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("[TENANT-MGMT] Nuevo salón registrado: '{}' (ID={}, slug={})", savedTenant.getName(), savedTenant.getId(), savedTenant.getSlug());

        // Aprovisionamiento inicial del administrador del salón
        provisionInitialAdmin(savedTenant, dto);

        return mapToResponseDto(savedTenant);
    }

    @Transactional
    public TenantResponseDto updateTenant(Long id, TenantUpdateRequestDto dto) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salón no encontrado con ID: " + id));

        String customDomain = dto.getCustomDomain() != null && !dto.getCustomDomain().isBlank()
                ? dto.getCustomDomain().trim().toLowerCase()
                : null;

        if (customDomain != null && !customDomain.equalsIgnoreCase(tenant.getCustomDomain()) && tenantRepository.existsByCustomDomainIgnoreCase(customDomain)) {
            throw new IllegalArgumentException("Ya existe un salón registrado con el dominio: " + customDomain);
        }

        if (dto.getActive() != null) {
            if (id.equals(1L) && !dto.getActive()) {
                throw new IllegalArgumentException("El salón principal BunnyCure (ID 1) no puede ser desactivado");
            }
            tenant.setActive(dto.getActive());
        }

        tenant.setName(dto.getName().trim());
        tenant.setCustomDomain(customDomain);
        tenant.setRut(dto.getRut());
        tenant.setPhone(dto.getPhone());
        tenant.setEmail(dto.getEmail());
        tenant.setAddress(dto.getAddress());
        tenant.setLogoUrl(dto.getLogoUrl());
        if (dto.getPrimaryColor() != null && !dto.getPrimaryColor().isBlank()) {
            tenant.setPrimaryColor(dto.getPrimaryColor());
        }
        if (dto.getPlanTier() != null && !dto.getPlanTier().isBlank()) {
            tenant.setPlanTier(dto.getPlanTier().toUpperCase());
        }
        tenant.setWhatsappPhoneId(dto.getWhatsappPhoneId());
        if (dto.getWhatsappToken() != null && !dto.getWhatsappToken().isBlank()) {
            tenant.setWhatsappToken(dto.getWhatsappToken());
        }

        Tenant updated = tenantRepository.save(tenant);
        log.info("[TENANT-MGMT] Salón actualizado: ID={}, nombre='{}'", updated.getId(), updated.getName());
        return mapToResponseDto(updated);
    }

    @Transactional
    public void toggleTenantStatus(Long id, boolean active) {
        if (id.equals(1L) && !active) {
            throw new IllegalArgumentException("El salón principal BunnyCure (ID 1) no puede ser desactivado");
        }
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salón no encontrado con ID: " + id));
        tenant.setActive(active);
        tenantRepository.save(tenant);
        log.info("[TENANT-MGMT] Estado de salón ID={} cambiado a active={}", id, active);
    }

    private void provisionInitialAdmin(Tenant tenant, TenantCreateRequestDto dto) {
        String adminUsername = dto.getInitialAdminUsername() != null && !dto.getInitialAdminUsername().isBlank()
                ? dto.getInitialAdminUsername().trim()
                : "admin_" + tenant.getSlug();

        String rawPassword = dto.getInitialAdminPassword() != null && !dto.getInitialAdminPassword().isBlank()
                ? dto.getInitialAdminPassword().trim()
                : "Salon" + tenant.getId() + "!" + UUID.randomUUID().toString().substring(0, 6);

        String adminFullName = dto.getInitialAdminFullName() != null && !dto.getInitialAdminFullName().isBlank()
                ? dto.getInitialAdminFullName().trim()
                : "Administrador(a) " + tenant.getName();

        String adminEmail = dto.getInitialAdminEmail() != null && !dto.getInitialAdminEmail().isBlank()
                ? dto.getInitialAdminEmail().trim()
                : (tenant.getEmail() != null ? tenant.getEmail().trim() : "admin@" + tenant.getSlug() + ".cl");

        tenantExecutionService.executeInTenantContext(tenant.getId(), () -> {
            User adminUser = User.builder()
                    .tenantId(tenant.getId())
                    .username(adminUsername)
                    .password(passwordEncoder.encode(rawPassword))
                    .fullName(adminFullName)
                    .email(adminEmail)
                    .enabled(true)
                    .role("ADMIN")
                    .passwordChangeRequired(true)
                    .build();

            userRepository.save(adminUser);
            log.info("[TENANT-MGMT] Administrador inicial aprovisionado para salón ID={}: username='{}'", tenant.getId(), adminUsername);
        });
    }

    private TenantResponseDto mapToResponseDto(Tenant tenant) {
        long userCount = 0;
        long appointmentCount = 0;
        long customerCount = 0;

        try {
            Number u = (Number) entityManager.createNativeQuery("SELECT count(*) FROM users WHERE tenant_id = :tid")
                    .setParameter("tid", tenant.getId())
                    .getSingleResult();
            userCount = u != null ? u.longValue() : 0;

            Number a = (Number) entityManager.createNativeQuery("SELECT count(*) FROM appointments WHERE tenant_id = :tid")
                    .setParameter("tid", tenant.getId())
                    .getSingleResult();
            appointmentCount = a != null ? a.longValue() : 0;

            Number c = (Number) entityManager.createNativeQuery("SELECT count(*) FROM customers WHERE tenant_id = :tid")
                    .setParameter("tid", tenant.getId())
                    .getSingleResult();
            customerCount = c != null ? c.longValue() : 0;
        } catch (Exception e) {
            log.debug("[TENANT-MGMT] No se pudieron obtener métricas para tenant {}: {}", tenant.getId(), e.getMessage());
        }

        return TenantResponseDto.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .customDomain(tenant.getCustomDomain())
                .rut(tenant.getRut())
                .phone(tenant.getPhone())
                .email(tenant.getEmail())
                .address(tenant.getAddress())
                .logoUrl(tenant.getLogoUrl())
                .primaryColor(tenant.getPrimaryColor())
                .active(tenant.isActive())
                .planTier(tenant.getPlanTier())
                .whatsappPhoneId(tenant.getWhatsappPhoneId())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .userCount(userCount)
                .appointmentCount(appointmentCount)
                .customerCount(customerCount)
                .build();
    }
}
