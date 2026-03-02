package cl.bunnycure.config;

import cl.bunnycure.domain.model.AppSettings;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppSettingsRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
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

    @Override
    public void run(String ... args) {

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
        if (customerRepository.count() == 0) {
            customerRepository.saveAll(List.of(
                    new Customer("María González",   "+56912345678", "maria@test.cl"),
                    new Customer("Valentina López",  "+56987654321", "vale@test.cl"),
                    new Customer("Javiera Muñoz",    "+56911223344", "javi@test.cl")
            ));
            log.info("✅ Clientes de prueba inicializados");
        }

        // ── Settings por defecto ─────────────────────────────────────────────
        if (appSettingsRepository.count() == 0) {
            appSettingsRepository.saveAll(List.of(
                    new AppSettings("booking.enabled",                  "true",  "Portal de reservas habilitado"),
                    new AppSettings("whatsapp.number",                  "56964499995", "Número WhatsApp negocio"),
                    new AppSettings("booking.message.template",
                            "Hola Bunny Cure! 🐰\nMe gustaría reservar una cita:\n• Servicio: {servicio}\n• Fecha: {fecha}\n• Bloque: {bloque}\n• Nombre: {nombre}\n• Teléfono: {telefono}\n¿Tienen disponibilidad?",
                            "Template mensaje WhatsApp"),
                    new AppSettings("booking.block.morning",            "09:00 – 13:00", "Bloque mañana"),
                    new AppSettings("booking.block.afternoon",          "15:00 – 18:00", "Bloque tarde"),
                    new AppSettings("booking.block.night",              "19:00 – 22:00", "Bloque noche"),
                    new AppSettings("booking.block.morning.enabled",    "true",  "Mañana habilitado"),
                    new AppSettings("booking.block.afternoon.enabled",  "true",  "Tarde habilitado"),
                    new AppSettings("booking.block.night.enabled",      "true",  "Noche habilitado")
            ));
            log.info("✅ Configuración inicial cargada");
        }
    }
}
