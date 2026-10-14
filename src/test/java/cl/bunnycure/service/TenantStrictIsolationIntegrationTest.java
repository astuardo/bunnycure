package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.BookingRequestStatus;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.Product;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.BookingRequestRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ProductRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
class TenantStrictIsolationIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BookingRequestRepository bookingRequestRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long customerId1;
    private Long customerId2;
    private Long serviceId1;
    private Long serviceId2;
    private Long productId1;
    private Long productId2;
    private Long bookingRequestId1;
    private Long bookingRequestId2;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        try {
            if (bookingRequestId1 != null) {
                TenantContext.setCurrentTenantId(1L);
                transactionTemplate.execute(status -> { bookingRequestRepository.deleteById(bookingRequestId1); return null; });
            }
            if (bookingRequestId2 != null) {
                TenantContext.setCurrentTenantId(2L);
                transactionTemplate.execute(status -> { bookingRequestRepository.deleteById(bookingRequestId2); return null; });
            }
            if (serviceId1 != null) {
                TenantContext.setCurrentTenantId(1L);
                transactionTemplate.execute(status -> { serviceCatalogRepository.deleteById(serviceId1); return null; });
            }
            if (serviceId2 != null) {
                TenantContext.setCurrentTenantId(2L);
                transactionTemplate.execute(status -> { serviceCatalogRepository.deleteById(serviceId2); return null; });
            }
            if (productId1 != null) {
                TenantContext.setCurrentTenantId(1L);
                transactionTemplate.execute(status -> { productRepository.deleteById(productId1); return null; });
            }
            if (productId2 != null) {
                TenantContext.setCurrentTenantId(2L);
                transactionTemplate.execute(status -> { productRepository.deleteById(productId2); return null; });
            }
            if (customerId1 != null) {
                TenantContext.setCurrentTenantId(1L);
                transactionTemplate.execute(status -> { customerRepository.deleteById(customerId1); return null; });
            }
            if (customerId2 != null) {
                TenantContext.setCurrentTenantId(2L);
                transactionTemplate.execute(status -> { customerRepository.deleteById(customerId2); return null; });
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Aislamiento Estricto de Clientes: Tenant 1 y Tenant 2 no comparten cartera de clientes")
    void strictIsolation_Customers() {
        // Tenant 1
        TenantContext.setCurrentTenantId(1L);
        customerId1 = transactionTemplate.execute(status -> {
            Customer c1 = new Customer("Clienta BunnyCure", "+56911111111", "clienta1@bunnycure.cl", "11.111.111-1");
            return customerRepository.save(c1).getId();
        });

        // Tenant 2
        TenantContext.setCurrentTenantId(2L);
        customerId2 = transactionTemplate.execute(status -> {
            Customer c2 = new Customer("Clienta Glamour", "+56922222222", "clienta2@glamour.cl", "22.222.222-2");
            return customerRepository.save(c2).getId();
        });

        // Verificación desde Tenant 2
        TenantContext.setCurrentTenantId(2L);
        transactionTemplate.execute(status -> {
            entityManager.clear();
            List<Customer> tenant2Customers = customerRepository.findAll();
            assertTrue(tenant2Customers.stream().anyMatch(c -> c.getId().equals(customerId2)));
            assertFalse(tenant2Customers.stream().anyMatch(c -> c.getId().equals(customerId1)), "Tenant 2 jamás debe ver a clientes de Tenant 1");

            Optional<Customer> leak = customerRepository.findById(customerId1);
            assertTrue(leak.isEmpty(), "findById del cliente de Tenant 1 debe ser invisible para Tenant 2");
            return null;
        });

        // Verificación desde Tenant 1
        TenantContext.setCurrentTenantId(1L);
        transactionTemplate.execute(status -> {
            entityManager.clear();
            List<Customer> tenant1Customers = customerRepository.findAll();
            assertTrue(tenant1Customers.stream().anyMatch(c -> c.getId().equals(customerId1)));
            assertFalse(tenant1Customers.stream().anyMatch(c -> c.getId().equals(customerId2)), "Tenant 1 jamás debe ver a clientes de Tenant 2");

            Optional<Customer> leak = customerRepository.findById(customerId2);
            assertTrue(leak.isEmpty(), "findById del cliente de Tenant 2 debe ser invisible para Tenant 1");
            return null;
        });
    }

    @Test
    @DisplayName("Aislamiento Estricto de Catálogo de Servicios: Cada salón tiene sus propios servicios y precios")
    void strictIsolation_ServiceCatalog() {
        // Tenant 1 crea servicio
        TenantContext.setCurrentTenantId(1L);
        serviceId1 = transactionTemplate.execute(status -> {
            ServiceCatalog s1 = ServiceCatalog.builder()
                    .name("Manicure Rusa Bunny")
                    .durationMinutes(60)
                    .price(new BigDecimal("25000.00"))
                    .active(true)
                    .displayOrder(1)
                    .build();
            return serviceCatalogRepository.save(s1).getId();
        });

        // Tenant 2 crea servicio con mismo o diferente nombre
        TenantContext.setCurrentTenantId(2L);
        serviceId2 = transactionTemplate.execute(status -> {
            ServiceCatalog s2 = ServiceCatalog.builder()
                    .name("Manicure Glamour VIP")
                    .durationMinutes(90)
                    .price(new BigDecimal("45000.00"))
                    .active(true)
                    .displayOrder(1)
                    .build();
            return serviceCatalogRepository.save(s2).getId();
        });

        // Verificación en Tenant 2
        TenantContext.setCurrentTenantId(2L);
        transactionTemplate.execute(status -> {
            entityManager.clear();
            List<ServiceCatalog> activeServices = serviceCatalogRepository.findByActiveTrueOrderByDisplayOrderAsc();
            assertTrue(activeServices.stream().anyMatch(s -> s.getId().equals(serviceId2)));
            assertFalse(activeServices.stream().anyMatch(s -> s.getId().equals(serviceId1)), "Servicios de Tenant 1 no deben aparecer en Tenant 2");

            Optional<ServiceCatalog> leak = serviceCatalogRepository.findById(serviceId1);
            assertTrue(leak.isEmpty(), "Tenant 2 no puede acceder al servicio de Tenant 1");
            return null;
        });
    }

    @Test
    @DisplayName("Aislamiento Estricto de Inventario: El stock y costos de insumos no se cruzan entre salones")
    void strictIsolation_Products() {
        // Tenant 1 crea producto
        TenantContext.setCurrentTenantId(1L);
        productId1 = transactionTemplate.execute(status -> {
            Product p1 = Product.builder()
                    .name("Esmalte OPI Rosa Bunny")
                    .purchasePrice(new BigDecimal("12000.00"))
                    .purchaseUnit("frasco")
                    .consumptionUnit("ml")
                    .conversionFactor(new BigDecimal("15"))
                    .stockConsumptionUnit(new BigDecimal("150"))
                    .build();
            return productRepository.save(p1).getId();
        });

        // Tenant 2 crea producto
        TenantContext.setCurrentTenantId(2L);
        productId2 = transactionTemplate.execute(status -> {
            Product p2 = Product.builder()
                    .name("Gel Polish Glamour Gold")
                    .purchasePrice(new BigDecimal("18500.00"))
                    .purchaseUnit("unidad")
                    .consumptionUnit("ml")
                    .conversionFactor(new BigDecimal("20"))
                    .stockConsumptionUnit(new BigDecimal("200"))
                    .build();
            return productRepository.save(p2).getId();
        });

        // Verificación en Tenant 1
        TenantContext.setCurrentTenantId(1L);
        transactionTemplate.execute(status -> {
            entityManager.clear();
            List<Product> products = productRepository.findAll();
            assertTrue(products.stream().anyMatch(p -> p.getId().equals(productId1)));
            assertFalse(products.stream().anyMatch(p -> p.getId().equals(productId2)), "Insumos de Tenant 2 no deben ser visibles para Tenant 1");

            Optional<Product> leak = productRepository.findById(productId2);
            assertTrue(leak.isEmpty(), "Tenant 1 no puede ver ni modificar insumos de Tenant 2");
            return null;
        });
    }
}
