package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.config.TestMailConfiguration;
import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.ServiceCatalog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
@DisplayName("Listado de citas: filtro por varios estados y orden por cercanía a hoy")
class AppointmentListOrderingIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private Long customerId;
    private Long serviceId;
    private Long pastCompletedId;
    private Long olderCompletedId;
    private Long todayPendingId;
    private Long tomorrowConfirmedId;
    private Long nextMonthPendingId;
    private Long cancelledId;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Santiago"));
        inTenant(() -> {
            Customer customer = new Customer();
            customer.setTenantId(1L);
            customer.setPublicId(UUID.randomUUID().toString());
            customer.setFullName("Orden Test");
            customer.setPhone("+5690000" + (int) (Math.random() * 1000));
            customer.setRut(("T" + UUID.randomUUID()).substring(0, 12));
            customerId = customerRepository.save(customer).getId();

            ServiceCatalog service = ServiceCatalog.builder()
                    .tenantId(1L).name("Servicio orden test").durationMinutes(60).price(BigDecimal.TEN).build();
            serviceId = serviceCatalogRepository.save(service).getId();

            olderCompletedId = save(customer, service, today.minusDays(10), "10:00", AppointmentStatus.COMPLETED);
            pastCompletedId = save(customer, service, today.minusDays(2), "10:00", AppointmentStatus.COMPLETED);
            nextMonthPendingId = save(customer, service, today.plusDays(30), "09:00", AppointmentStatus.PENDING);
            tomorrowConfirmedId = save(customer, service, today.plusDays(1), "15:00", AppointmentStatus.CONFIRMED);
            todayPendingId = save(customer, service, today, "18:00", AppointmentStatus.PENDING);
            cancelledId = save(customer, service, today.plusDays(3), "11:00", AppointmentStatus.CANCELLED);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        inTenant(() -> {
            appointmentRepository.deleteAllById(List.of(pastCompletedId, olderCompletedId, todayPendingId,
                    tomorrowConfirmedId, nextMonthPendingId, cancelledId));
            customerRepository.deleteById(customerId);
            serviceCatalogRepository.deleteById(serviceId);
            return null;
        });
        TenantContext.clear();
    }

    @Test
    @DisplayName("nearest: próximas citas ascendente y luego historial descendente, ignorando el sort del Pageable")
    void nearestOrder_UpcomingAscendingThenPastDescending() {
        Page<Appointment> page = inTenant(() -> appointmentService.findPaged(customerId, null, null, null, null, true,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "appointmentDate"))));

        assertThat(page.getContent()).extracting(Appointment::getId).containsExactly(
                todayPendingId, tomorrowConfirmedId, cancelledId, nextMonthPendingId, pastCompletedId, olderCompletedId);
        assertThat(page.getTotalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("statuses: filtra por varios estados a la vez")
    void multipleStatuses_FilterOnServer() {
        Page<Appointment> page = inTenant(() -> appointmentService.findPaged(customerId,
                Set.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED), null, null, null, true, PageRequest.of(0, 50)));

        assertThat(page.getContent()).extracting(Appointment::getId)
                .containsExactly(todayPendingId, tomorrowConfirmedId, nextMonthPendingId);
    }

    private Long save(Customer customer, ServiceCatalog service, LocalDate date, String time, AppointmentStatus status) {
        Appointment appointment = Appointment.builder()
                .tenantId(1L)
                .customer(customer)
                .service(service)
                .appointmentDate(date)
                .appointmentTime(LocalTime.parse(time))
                .status(status)
                .build();
        return appointmentRepository.save(appointment).getId();
    }

    private <T> T inTenant(Supplier<T> action) {
        Long previous = TenantContext.getExplicitTenantId();
        try {
            TenantContext.setCurrentTenantId(1L);
            return transactionTemplate.execute(status -> action.get());
        } finally {
            TenantContext.setCurrentTenantId(previous);
        }
    }
}
