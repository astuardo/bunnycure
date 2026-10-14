package cl.bunnycure.service;

import cl.bunnycure.config.TenantContext;
import cl.bunnycure.domain.enums.InvoiceOutboxStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceOutbox;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.CustomerRepository;
import cl.bunnycure.domain.repository.InvoiceOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de Resiliencia para Facturación Electrónica (Outbox Pattern).
 * Garantiza que ninguna emisión de boleta se pierda ante indisponibilidad
 * temporal del SII, caídas de red o timeouts en la API.
 */
@Slf4j
@Service
public class InvoiceOutboxService {

    private final InvoiceOutboxRepository outboxRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final TenantExecutionService tenantExecutionService;
    private final ApiGatewaySiiService apiGatewaySiiService;

    public InvoiceOutboxService(
            InvoiceOutboxRepository outboxRepository,
            AppointmentRepository appointmentRepository,
            CustomerRepository customerRepository,
            TenantExecutionService tenantExecutionService,
            @Lazy ApiGatewaySiiService apiGatewaySiiService) {
        this.outboxRepository = outboxRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.tenantExecutionService = tenantExecutionService;
        this.apiGatewaySiiService = apiGatewaySiiService;
    }

    /**
     * Encola una boleta para ser emitida de manera confiable con reintentos automáticos.
     */
    @Transactional
    public InvoiceOutbox enqueueInvoice(Appointment appointment, Customer customer, BigDecimal amount) {
        Long tenantId = TenantContext.getCurrentTenantId();

        Optional<InvoiceOutbox> existing = outboxRepository.findByAppointmentId(appointment.getId());
        if (existing.isPresent()) {
            InvoiceOutbox item = existing.get();
            if (item.getStatus() == InvoiceOutboxStatus.COMPLETED) {
                log.info("[INVOICE-OUTBOX] La cita {} ya tiene una boleta emitida exitosamente (folio={})",
                        appointment.getId(), item.getFolio());
                return item;
            }
            if (item.getStatus() == InvoiceOutboxStatus.FAILED) {
                // Reintentar si había fallado previamente
                item.setStatus(InvoiceOutboxStatus.PENDING);
                item.setAttempts(0);
                item.setNextAttemptAt(LocalDateTime.now());
                item.setLastError(null);
                log.info("[INVOICE-OUTBOX] Reactivando emisión previamente fallida para cita {}", appointment.getId());
                return outboxRepository.save(item);
            }
            log.info("[INVOICE-OUTBOX] Boleta para cita {} ya está encolada (status={})", appointment.getId(), item.getStatus());
            return item;
        }

        InvoiceOutbox outbox = InvoiceOutbox.builder()
                .tenantId(tenantId)
                .appointmentId(appointment.getId())
                .customerId(customer.getId())
                .customerRut(customer.getRut() != null ? customer.getRut() : "")
                .customerName(customer.getFullName())
                .customerEmail(customer.getEmail())
                .amount(amount)
                .status(InvoiceOutboxStatus.PENDING)
                .attempts(0)
                .maxAttempts(5)
                .nextAttemptAt(LocalDateTime.now())
                .build();

        InvoiceOutbox saved = outboxRepository.save(outbox);
        log.info("[INVOICE-OUTBOX] Boleta encolada para cita {} - Salón ID={} - Monto={}",
                appointment.getId(), tenantId, amount);
        return saved;
    }

    /**
     * Tarea programada para despachar boletas pendientes con backoff exponencial.
     * Se ejecuta cada 60 segundos por defecto.
     */
    @Scheduled(fixedDelayString = "${bunnycure.invoice.outbox.delay-ms:60000}")
    public void processPendingInvoices() {
        LocalDateTime now = LocalDateTime.now();
        List<InvoiceOutbox> pendingItems = outboxRepository.findPendingToProcess(
                List.of(InvoiceOutboxStatus.PENDING),
                now,
                PageRequest.of(0, 10)
        );

        if (pendingItems.isEmpty()) {
            return;
        }

        log.info("[INVOICE-OUTBOX] Procesando lote de {} boletas pendientes de emisión...", pendingItems.size());

        for (InvoiceOutbox item : pendingItems) {
            processOutboxItem(item);
        }
    }

    private void processOutboxItem(InvoiceOutbox item) {
        Long targetTenantId = item.getTenantId();

        tenantExecutionService.executeInTenantContext(targetTenantId, () -> {
            try {
                item.setStatus(InvoiceOutboxStatus.PROCESSING);
                outboxRepository.save(item);

                Optional<Appointment> appointmentOpt = appointmentRepository.findById(item.getAppointmentId());
                Optional<Customer> customerOpt = customerRepository.findById(item.getCustomerId());

                if (appointmentOpt.isEmpty() || customerOpt.isEmpty()) {
                    item.setStatus(InvoiceOutboxStatus.FAILED);
                    item.setLastError("Cita o cliente asociado no existe en la base de datos");
                    outboxRepository.save(item);
                    log.error("[INVOICE-OUTBOX] Error procesando item {}: cita o cliente no encontrado", item.getId());
                    return;
                }

                Appointment appointment = appointmentOpt.get();
                Customer customer = customerOpt.get();

                Optional<String> folioOpt = apiGatewaySiiService.generateInvoice(appointment, customer, item.getAmount());

                if (folioOpt.isPresent() && !folioOpt.get().isBlank()) {
                    item.setStatus(InvoiceOutboxStatus.COMPLETED);
                    item.setFolio(folioOpt.get());
                    item.setLastError(null);
                    outboxRepository.save(item);
                    log.info("[INVOICE-OUTBOX] ? Boleta emitida con éxito para cita {}! Folio: {}",
                            appointment.getId(), folioOpt.get());
                } else {
                    handleFailure(item, "La API no retornó folio válido o está en modo dry-run");
                }
            } catch (Exception e) {
                log.error("[INVOICE-OUTBOX] Error al emitir boleta para cita {}: {}",
                        item.getAppointmentId(), e.getMessage(), e);
                handleFailure(item, e.getMessage());
            }
        });
    }

    private void handleFailure(InvoiceOutbox item, String errorMsg) {
        int currentAttempts = item.getAttempts() + 1;
        item.setAttempts(currentAttempts);
        item.setLastError(errorMsg != null && errorMsg.length() > 950 ? errorMsg.substring(0, 950) : errorMsg);

        if (currentAttempts >= item.getMaxAttempts()) {
            item.setStatus(InvoiceOutboxStatus.FAILED);
            log.warn("[INVOICE-OUTBOX] ?? Boleta para cita {} superó el límite de reintentos ({}/{}). Marcada como FAILED.",
                    item.getAppointmentId(), currentAttempts, item.getMaxAttempts());
        } else {
            item.setStatus(InvoiceOutboxStatus.PENDING);
            // Backoff exponencial: 2, 4, 8, 16 minutos
            long delayMinutes = (long) Math.pow(2, currentAttempts);
            item.setNextAttemptAt(LocalDateTime.now().plusMinutes(delayMinutes));
            log.info("[INVOICE-OUTBOX] Programado reintento {}/{} para cita {} en {} minutos",
                    currentAttempts, item.getMaxAttempts(), item.getAppointmentId(), delayMinutes);
        }

        outboxRepository.save(item);
    }

    /**
     * Permite a un administrador forzar el reintento de una boleta fallida.
     */
    @Transactional
    public void retryFailedInvoice(Long outboxId) {
        InvoiceOutbox item = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Registro de Outbox no encontrado con ID: " + outboxId));

        item.setStatus(InvoiceOutboxStatus.PENDING);
        item.setAttempts(0);
        item.setLastError(null);
        item.setNextAttemptAt(LocalDateTime.now());
        outboxRepository.save(item);
        log.info("[INVOICE-OUTBOX] Reintento manual solicitado para item {}", outboxId);
    }
}
