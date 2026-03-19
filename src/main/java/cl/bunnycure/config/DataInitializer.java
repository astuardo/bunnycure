package cl.bunnycure.config;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.model.User;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import cl.bunnycure.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final CustomerRepository      customerRepository;
    private final ServiceCatalogRepository serviceCatalogRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bunnycure.admin.username:admin}")
    private String adminUsername;

    @Value("${bunnycure.admin.password:changeme-local-only}")
    private String adminPassword;

    @Value("${bunnycure.admin.full-name:Administrador Local}")
    private String adminFullName;

    @Value("${bunnycure.admin.email:admin@local.test}")
    private String adminEmail;

    @Value("${bunnycure.demo.customers.enabled:true}")
    private boolean demoCustomersEnabled;

    @Override
    public void run(String ... args) {

        // ── Usuario Admin ────────────────────────────────────────────────────
        // Buscar si ya existe el usuario admin
        User adminUser = userRepository.findByUsername(adminUsername).orElse(null);
        
        if (adminUser == null) {
            // No existe, crear uno nuevo
            adminUser = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName(adminFullName)
                    .email(adminEmail)
                    .enabled(true)
                    .role("ADMIN")
                    .build();
            
            userRepository.save(adminUser);
            log.info("✅ Usuario admin local creado (username: {})", adminUsername);
        } else {
            // Ya existe, asegurar estado habilitado y sincronizar contraseña configurada.
            adminUser.setEnabled(true);

            if (adminPassword != null && !adminPassword.isBlank()
                    && !passwordEncoder.matches(adminPassword, adminUser.getPassword())) {
                adminUser.setPassword(passwordEncoder.encode(adminPassword));
                log.info("✅ Usuario admin local actualizado con password desde configuración (username: {})", adminUsername);
            }

            userRepository.save(adminUser);
            log.info("✅ Usuario admin local verificado (username: {})", adminUsername);
        }

        // ── Servicios ────────────────────────────────────────────────────────
        if (serviceCatalogRepository.count() == 0) {
            serviceCatalogRepository.saveAll(List.of(
                    ServiceCatalog.builder()
                            .name("Manicure + Brillo")
                            .durationMinutes(60)
                            .price(new BigDecimal("10000"))
                            .active(true).displayOrder(1).build(),

                    ServiceCatalog.builder()
                            .name("Manicure Semi-Permanente")
                            .durationMinutes(90)
                            .price(new BigDecimal("20000"))
                            .active(true).displayOrder(2).build(),

                    ServiceCatalog.builder()
                            .name("Manicure Men")
                            .durationMinutes(90)
                            .price(new BigDecimal("15000"))
                            .active(true).displayOrder(3).build(),

                    ServiceCatalog.builder()
                            .name("Kapping Gel")
                            .durationMinutes(120)
                            .price(new BigDecimal("22000"))
                            .active(true).displayOrder(4).build(),

                    ServiceCatalog.builder()
                            .name("Soft Gel")
                            .durationMinutes(150)
                            .price(new BigDecimal("27000"))
                            .active(true).displayOrder(5).build(),

                    ServiceCatalog.builder()
                            .name("Polygel Esculpido")
                            .durationMinutes(210)
                            .price(new BigDecimal("35000"))
                            .active(true).displayOrder(6).build(),

                    ServiceCatalog.builder()
                            .name("Pedicure + Esmaltado")
                            .durationMinutes(90)
                            .price(new BigDecimal("22000"))
                            .active(true).displayOrder(7).build(),

                    ServiceCatalog.builder()
                            .name("Pedicure + Esmaltado + Spa")
                            .durationMinutes(150)
                            .price(new BigDecimal("25000"))
                            .active(true).displayOrder(8).build(),

                    ServiceCatalog.builder()
                            .name("Nail Art")
                            .durationMinutes(240)
                            .price(new BigDecimal("30000"))
                            .active(true).displayOrder(9).build(),

                    ServiceCatalog.builder()
                            .name("Retiro de Esmalte")
                            .durationMinutes(60)
                            .price(new BigDecimal("7000"))
                            .active(true).displayOrder(10).build()
            ));
            log.info("✅ Catálogo de servicios inicializado ({} servicios)",
                    serviceCatalogRepository.count());
        }

        // ── Clientes de prueba ───────────────────────────────────────────────
        if (demoCustomersEnabled && customerRepository.count() == 0) {
            customerRepository.saveAll(List.of(
                    new Customer("Cliente Demo 1", "+56990000001", "demo1@local.test"),
                    new Customer("Cliente Demo 2", "+56990000002", "demo2@local.test"),
                    new Customer("Cliente Demo 3", "+56990000003", "demo3@local.test")
            ));
            log.info("✅ Clientes de prueba inicializados");
        } else if (!demoCustomersEnabled) {
            log.info("ℹ️ Seed de clientes demo deshabilitado por configuración");
        }

        // ── Settings por defecto ─────────────────────────────────────────────
        if (appSettingsRepository.count() == 0) {
            appSettingsRepository.saveAll(List.of(
                    new AppSettings("booking.enabled",                  "true",  "Portal de reservas habilitado"),
                    new AppSettings("whatsapp.number",                  "56990000010", "Número WhatsApp humano (legacy)"),
                    new AppSettings("whatsapp.human.number",            "56990000010", "Número WhatsApp atención humana"),
                    new AppSettings("whatsapp.admin-alert.number",      "56990000011", "Número WhatsApp alertas internas de reservas"),
                    new AppSettings("whatsapp.human.display-name",      "Equipo de soporte", "Nombre visible atención humana"),
                    new AppSettings("whatsapp.handoff.enabled",         "true", "Habilita derivación a atención humana"),
                    new AppSettings("whatsapp.handoff.client-message",  "Si necesitas ayuda personalizada, escríbenos al WhatsApp de atención humana: {numero}.", "Mensaje de derivación al cliente"),
                    new AppSettings("whatsapp.handoff.admin-prefill",   "Hola {nombre}, te escribimos por tu solicitud o cita. Te contacto para ayudarte personalmente.", "Mensaje prellenado para atención manual"),
                    new AppSettings("whatsapp.template.confirmation.name", "confirmacion_cita", "Template WhatsApp para confirmación de cita"),
                    new AppSettings("whatsapp.template.reminder.name", "recordatorio_cita", "Template WhatsApp para recordatorio de cita"),
                    new AppSettings("whatsapp.template.cancellation.name", "cancelacion_cita", "Template WhatsApp para cancelación de cita"),
                    new AppSettings("whatsapp.template.booking-review.name", "agenda_en_revision", "Template WhatsApp para agenda en revisión"),
                    new AppSettings("whatsapp.template.booking-rejected.name", "solicitud_rechazada", "Template WhatsApp para solicitud rechazada"),
                    new AppSettings("whatsapp.template.admin-alert.name", "", "Template WhatsApp para alertas internas al admin"),
                    new AppSettings("whatsapp.template.language", "es_CL", "Idioma default para templates WhatsApp"),
                    new AppSettings("whatsapp.template.admin-alert.language", "es_CL", "Idioma para template de alerta admin"),
                    new AppSettings("whatsapp.template.confirmation.enabled", "true", "Habilita template de confirmación"),
                    new AppSettings("whatsapp.template.reminder.enabled", "true", "Habilita template de recordatorio"),
                    new AppSettings("whatsapp.template.cancellation.enabled", "true", "Habilita template de cancelación"),
                    new AppSettings("whatsapp.template.booking-review.enabled", "true", "Habilita template de agenda en revisión"),
                    new AppSettings("whatsapp.template.booking-rejected.enabled", "true", "Habilita template de solicitud rechazada"),
                    new AppSettings("whatsapp.template.admin-alert.enabled", "false", "Habilita template de alerta admin"),
                    new AppSettings("whatsapp.admin.booking-requests.url", "", "URL del panel admin para solicitudes de reserva"),
                    new AppSettings("whatsapp.business.name", "Negocio Demo", "Nombre del negocio para contexto WhatsApp"),
                    new AppSettings("booking.message.template",
                            "Hola Bunny Cure! [conejo]\nMe gustar\u00EDa reservar una cita:\n\u2022 Servicio: {servicio}\n\u2022 Fecha: {fecha}\n\u2022 Bloque: {bloque}\n\u2022 Nombre: {nombre}\n\u2022 Tel\u00E9fono: {telefono}\n\u00BFTienen disponibilidad?",
                            "Template mensaje WhatsApp"),
                    new AppSettings("booking.block.morning",            "09:00 – 13:00", "Bloque mañana"),
                    new AppSettings("booking.block.afternoon",          "15:00 – 18:00", "Bloque tarde"),
                    new AppSettings("booking.block.night",              "19:00 – 22:00", "Bloque noche"),
                    new AppSettings("booking.block.morning.enabled",    "true",  "Mañana habilitado"),
                    new AppSettings("booking.block.afternoon.enabled",  "true",  "Tarde habilitado"),
                    new AppSettings("booking.block.night.enabled",      "true",  "Noche habilitado"),
                    new AppSettings("reminder.strategy",                "2hours",
                            "Estrategia de recordatorios: 2hours | morning | day_before | both")
            ));
            log.info("✅ Configuración inicial cargada");
        }
    }
}