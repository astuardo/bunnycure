package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.enums.InvoiceOutboxStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceOutbox;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.InvoiceOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceOutboxServiceTest {

    @Mock
    private InvoiceOutboxRepository outboxRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TenantExecutionService tenantExecutionService;

    @Mock
    private ApiGatewaySiiService apiGatewaySiiService;

    private InvoiceOutboxService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceOutboxService(
                outboxRepository,
                appointmentRepository,
                customerRepository,
                tenantExecutionService,
                apiGatewaySiiService
        );
        TenantContext.setCurrentTenantId(2L);

        // Configurar tenantExecutionService para ejecutar el Runnable directamente
        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(tenantExecutionService).executeInTenantContext(anyLong(), any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void enqueueInvoice_CreatesPendingOutboxRecord() {
        Appointment appointment = Appointment.builder().id(101L).build();
        Customer customer = new Customer();
        customer.setId(202L);
        customer.setRut("18.664.589-8");
        customer.setFullName("Camila Silva");
        customer.setEmail("camila@test.cl");
        BigDecimal amount = new BigDecimal("25000");

        when(outboxRepository.findByAppointmentId(101L)).thenReturn(Optional.empty());
        when(outboxRepository.save(any(InvoiceOutbox.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceOutbox result = service.enqueueInvoice(appointment, customer, amount);

        assertNotNull(result);
        assertEquals(2L, result.getTenantId());
        assertEquals(101L, result.getAppointmentId());
        assertEquals(202L, result.getCustomerId());
        assertEquals("18.664.589-8", result.getCustomerRut());
        assertEquals(new BigDecimal("25000"), result.getAmount());
        assertEquals(InvoiceOutboxStatus.PENDING, result.getStatus());
        assertEquals(0, result.getAttempts());
        assertNotNull(result.getNextAttemptAt());

        verify(outboxRepository).save(any(InvoiceOutbox.class));
    }

    @Test
    void enqueueInvoice_DoesNotDuplicateCompletedInvoice() {
        Appointment appointment = Appointment.builder().id(101L).build();
        Customer customer = new Customer();
        customer.setId(202L);
        BigDecimal amount = new BigDecimal("25000");

        InvoiceOutbox existing = InvoiceOutbox.builder()
                .id(1L)
                .appointmentId(101L)
                .status(InvoiceOutboxStatus.COMPLETED)
                .folio("18234")
                .build();

        when(outboxRepository.findByAppointmentId(101L)).thenReturn(Optional.of(existing));

        InvoiceOutbox result = service.enqueueInvoice(appointment, customer, amount);

        assertEquals(InvoiceOutboxStatus.COMPLETED, result.getStatus());
        assertEquals("18234", result.getFolio());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void processPendingInvoices_SuccessfulEmissionMarksCompleted() {
        InvoiceOutbox item = InvoiceOutbox.builder()
                .id(1L)
                .tenantId(2L)
                .appointmentId(101L)
                .customerId(202L)
                .amount(new BigDecimal("25000"))
                .status(InvoiceOutboxStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Appointment appointment = Appointment.builder().id(101L).build();
        Customer customer = new Customer();
        customer.setId(202L);
        customer.setRut("18.664.589-8");

        when(outboxRepository.findPendingToProcess(anyList(), any(), any(Pageable.class)))
                .thenReturn(List.of(item));
        when(appointmentRepository.findById(101L)).thenReturn(Optional.of(appointment));
        when(customerRepository.findById(202L)).thenReturn(Optional.of(customer));
        when(apiGatewaySiiService.generateInvoice(appointment, customer, new BigDecimal("25000")))
                .thenReturn(Optional.of("54321"));

        service.processPendingInvoices();

        assertEquals(InvoiceOutboxStatus.COMPLETED, item.getStatus());
        assertEquals("54321", item.getFolio());
        assertNull(item.getLastError());
        verify(outboxRepository, atLeastOnce()).save(item);
    }

    @Test
    void processPendingInvoices_FailedEmissionIncrementsAttemptsAndBackoff() {
        InvoiceOutbox item = InvoiceOutbox.builder()
                .id(1L)
                .tenantId(2L)
                .appointmentId(101L)
                .customerId(202L)
                .amount(new BigDecimal("25000"))
                .status(InvoiceOutboxStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Appointment appointment = Appointment.builder().id(101L).build();
        Customer customer = new Customer();
        customer.setId(202L);
        customer.setRut("18.664.589-8");

        when(outboxRepository.findPendingToProcess(anyList(), any(), any(Pageable.class)))
                .thenReturn(List.of(item));
        when(appointmentRepository.findById(101L)).thenReturn(Optional.of(appointment));
        when(customerRepository.findById(202L)).thenReturn(Optional.of(customer));
        when(apiGatewaySiiService.generateInvoice(appointment, customer, new BigDecimal("25000")))
                .thenReturn(Optional.empty());

        service.processPendingInvoices();

        assertEquals(InvoiceOutboxStatus.PENDING, item.getStatus());
        assertEquals(1, item.getAttempts());
        assertNotNull(item.getLastError());
        assertTrue(item.getNextAttemptAt().isAfter(LocalDateTime.now()));
        verify(outboxRepository, atLeastOnce()).save(item);
    }

    @Test
    void retryFailedInvoice_ResetsStatusToPending() {
        InvoiceOutbox failedItem = InvoiceOutbox.builder()
                .id(5L)
                .status(InvoiceOutboxStatus.FAILED)
                .attempts(5)
                .lastError("SII timeout")
                .build();

        when(outboxRepository.findById(5L)).thenReturn(Optional.of(failedItem));

        service.retryFailedInvoice(5L);

        assertEquals(InvoiceOutboxStatus.PENDING, failedItem.getStatus());
        assertEquals(0, failedItem.getAttempts());
        assertNull(failedItem.getLastError());
        verify(outboxRepository).save(failedItem);
    }
}
