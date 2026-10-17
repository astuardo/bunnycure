package cl.bunnycure.service;

import cl.bunnycure.service.sii.ChileanRut;
import cl.bunnycure.service.sii.SiiResponseParser;
import cl.bunnycure.config.ApiGatewayConfig;
import cl.bunnycure.domain.enums.AppointmentStatus;
import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.InvoiceLog;
import cl.bunnycure.domain.repository.AppointmentRepository;
import cl.bunnycure.domain.repository.InvoiceLogRepository;
import cl.bunnycure.web.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import cl.bunnycure.service.sii.InvoiceLogMapper;

/**
 * Informes de boletas de honorarios: resumen del mes, atenciones pendientes de boleta,
 * boletas emitidas y conciliación con lo registrado en el SII.
 * Separado de ApiGatewaySiiService, que queda a cargo de emitir y consultar en el SII.
 */
@Slf4j
@Service
public class InvoiceReportService {

    private final ApiGatewayConfig config;
    private final InvoiceLogRepository invoiceLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final ApiGatewaySiiService siiService;

    public InvoiceReportService(ApiGatewayConfig config, InvoiceLogRepository invoiceLogRepository,
                                AppointmentRepository appointmentRepository, ApiGatewaySiiService siiService) {
        this.config = config;
        this.invoiceLogRepository = invoiceLogRepository;
        this.appointmentRepository = appointmentRepository;
        this.siiService = siiService;
    }

    public long getGeneratedInvoicesThisMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate firstDayNextMonth = firstDay.plusMonths(1);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = firstDayNextMonth.atStartOfDay();
        return invoiceLogRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("SUCCESS", start,
                end);
    }

    /**
     * Resumen de métricas y KPIs del mes actual (100% datos locales, 0 créditos de API)
     */
    public InvoiceSummaryDto getSummary() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate firstDayNextMonth = firstDay.plusMonths(1);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = firstDayNextMonth.atStartOfDay();

        long generatedThisMonth = invoiceLogRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("SUCCESS", start, end);
        long pendingInvoicesCount = appointmentRepository.countCompletedAppointmentsWithoutSuccessfulInvoice(AppointmentStatus.COMPLETED);
        long failedInvoicesCount = invoiceLogRepository.countByStatus("FAILED");
        BigDecimal totalAmountMonth = invoiceLogRepository.sumAmountByStatusSuccessAndCreatedAtBetween(start, end);

        return InvoiceSummaryDto.builder()
                .generatedThisMonth(generatedThisMonth)
                .pendingInvoicesCount(pendingInvoicesCount)
                .failedInvoicesCount(failedInvoicesCount)
                .totalAmountMonth(totalAmountMonth != null ? totalAmountMonth : BigDecimal.ZERO)
                .apiGatewayConfigured(config.isConfigured())
                .emisorRut(ChileanRut.sanitizeRut(config.getSiiRut()))
                .sendEmailEnabled(config.isSendEmailOnIssue())
                .build();
    }

    /**
     * Retorna citas completadas sin boleta exitosa para trazabilidad (100% datos locales, 0 créditos de API)
     */
    public List<InvoicePendingAppointmentDto> getPendingInvoices(LocalDate start, LocalDate end) {
        List<Appointment> appointments;
        if (start != null && end != null) {
            appointments = appointmentRepository.findCompletedAppointmentsWithoutSuccessfulInvoiceBetween(
                    AppointmentStatus.COMPLETED, start, end);
        } else {
            appointments = appointmentRepository.findCompletedAppointmentsWithoutSuccessfulInvoice(
                    AppointmentStatus.COMPLETED);
        }

        return appointments.stream().map(a -> {
            Customer c = a.getCustomer();
            Optional<InvoiceLog> logOpt = invoiceLogRepository.findByAppointmentId(a.getId());

            BigDecimal totalAmount = BigDecimal.ZERO;
            if (a.getServices() != null && !a.getServices().isEmpty()) {
                totalAmount = a.getServices().stream()
                        .map(s -> s.getPrice() != null ? BigDecimal.valueOf(s.getPrice().doubleValue()) : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (totalAmount.compareTo(BigDecimal.ZERO) <= 0 && a.getService() != null && a.getService().getPrice() != null) {
                totalAmount = BigDecimal.valueOf(a.getService().getPrice().doubleValue());
            }

            String rut = c != null ? c.getRut() : null;
            String rutStatus;
            boolean rutValid = false;
            if (rut == null || rut.isBlank()) {
                rutStatus = "MISSING";
            } else if (ChileanRut.validateRutFormat(rut)) {
                rutStatus = "VALID";
                rutValid = true;
            } else {
                rutStatus = "INVALID";
            }

            String invoiceStatus = "NOT_ATTEMPTED";
            String errorMsg = null;
            LocalDateTime lastAttempt = null;
            Long logId = null;

            if (logOpt.isPresent()) {
                InvoiceLog l = logOpt.get();
                logId = l.getId();
                invoiceStatus = l.getStatus() != null ? l.getStatus() : "FAILED";
                errorMsg = l.getErrorMessage();
                lastAttempt = l.getUpdatedAt() != null ? l.getUpdatedAt() : l.getCreatedAt();
            }

            String servicesSummary = a.getServices() != null && !a.getServices().isEmpty()
                    ? a.getServices().stream().map(s -> s.getName()).collect(Collectors.joining(", "))
                    : (a.getService() != null ? a.getService().getName() : "Servicio");

            boolean canEmit = rutValid && totalAmount.compareTo(BigDecimal.ZERO) > 0;

            return InvoicePendingAppointmentDto.builder()
                    .appointmentId(a.getId())
                    .appointmentDate(a.getAppointmentDate())
                    .appointmentTime(a.getAppointmentTime())
                    .customerId(c != null ? c.getId() : null)
                    .customerName(c != null ? c.getFullName() : "Sin Cliente")
                    .customerRut(rut)
                    .customerEmail(c != null ? c.getEmail() : null)
                    .customerPhone(c != null ? c.getPhone() : null)
                    .servicesSummary(servicesSummary)
                    .specialistName(a.getSpecialist() != null ? a.getSpecialist().getFullName() : "No asignada")
                    .totalAmount(totalAmount)
                    .invoiceLogId(logId)
                    .invoiceStatus(invoiceStatus)
                    .errorMessage(errorMsg)
                    .lastAttemptAt(lastAttempt)
                    .rutStatus(rutStatus)
                    .canEmit(canEmit)
                    .build();
        }).toList();
    }

    /**
     * Retorna boletas emitidas localmente en un período (100% datos locales, 0 créditos de API)
     */
    public List<InvoiceIssuedItemDto> getLocalIssuedInvoices(String periodo) {
        LocalDateTime start;
        LocalDateTime end;

        if (periodo != null && periodo.length() == 6) { // YYYYMM
            try {
                int year = Integer.parseInt(periodo.substring(0, 4));
                int month = Integer.parseInt(periodo.substring(4, 6));
                LocalDate firstDay = LocalDate.of(year, month, 1);
                LocalDate firstDayNextMonth = firstDay.plusMonths(1);
                start = firstDay.atStartOfDay();
                end = firstDayNextMonth.atStartOfDay();
            } catch (Exception e) {
                LocalDate today = LocalDate.now();
                LocalDate firstDay = today.withDayOfMonth(1);
                LocalDate firstDayNextMonth = firstDay.plusMonths(1);
                start = firstDay.atStartOfDay();
                end = firstDayNextMonth.atStartOfDay();
            }
        } else {
            LocalDate today = LocalDate.now();
            LocalDate firstDay = today.withDayOfMonth(1);
            LocalDate firstDayNextMonth = firstDay.plusMonths(1);
            start = firstDay.atStartOfDay();
            end = firstDayNextMonth.atStartOfDay();
        }

        List<InvoiceLog> logs = invoiceLogRepository.findByStatusAndCreatedAtBetweenWithDetails("SUCCESS", start, end);
        return logs.stream().map(InvoiceLogMapper::toIssuedItemDto).toList();
    }

    /**
     * Realiza el contraste entre los documentos del SII y las boletas/citas de BunnyCure.
     * Utiliza caché por defecto para ahorrar créditos de API a menos que se fuerce la recarga.
     */
    public InvoiceContrastResultDto contrastWithSii(String periodo, boolean forceRefresh) {
        String effectivePeriodo = (periodo != null && !periodo.isBlank())
                ? periodo
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        List<InvoiceIssuedItemDto> localInvoices = getLocalIssuedInvoices(effectivePeriodo);

        // Obtenemos fechas para el período
        int year;
        int month;
        try {
            year = Integer.parseInt(effectivePeriodo.substring(0, 4));
            month = Integer.parseInt(effectivePeriodo.substring(4, 6));
        } catch (Exception e) {
            LocalDate now = LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        List<InvoicePendingAppointmentDto> pendingAppointments = getPendingInvoices(start, end);

        String cacheKey = effectivePeriodo + "_1";
        boolean fromCache = !forceRefresh && siiService.hasFreshIssuedListCache(cacheKey);

        JsonNode siiResponse = siiService.listIssuedInvoices(effectivePeriodo, 1, forceRefresh);

        // Extraer folios de SII para comparar
        Set<String> siiFolios = new HashSet<>();
        BigDecimal siiTotalAmount = BigDecimal.ZERO;
        int siiTotalCount = 0;

        JsonNode docsArray = null;
        if (siiResponse != null) {
            if (siiResponse.isArray()) {
                docsArray = siiResponse;
            } else if (siiResponse.has("documentos") && siiResponse.get("documentos").isArray()) {
                docsArray = siiResponse.get("documentos");
            } else if (siiResponse.has("data") && siiResponse.get("data").isArray()) {
                docsArray = siiResponse.get("data");
            }
        }

        if (docsArray != null) {
            for (JsonNode doc : docsArray) {
                siiTotalCount++;
                String folio = SiiResponseParser.extractFolio(doc);
                if (folio != null && !folio.equals("N/A")) {
                    siiFolios.add(folio.trim());
                }
                if (doc.has("MntTotal")) {
                    siiTotalAmount = siiTotalAmount.add(BigDecimal.valueOf(doc.get("MntTotal").asDouble()));
                } else if (doc.has("monto")) {
                    siiTotalAmount = siiTotalAmount.add(BigDecimal.valueOf(doc.get("monto").asDouble()));
                } else if (doc.has("total")) {
                    siiTotalAmount = siiTotalAmount.add(BigDecimal.valueOf(doc.get("total").asDouble()));
                }
            }
        }

        BigDecimal localTotalAmount = localInvoices.stream()
                .map(i -> i.getAmountInClp() != null ? i.getAmountInClp() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int matchedCount = 0;
        for (InvoiceIssuedItemDto local : localInvoices) {
            if (local.getInvoiceNumber() != null && siiFolios.contains(local.getInvoiceNumber().trim())) {
                matchedCount++;
            }
        }

        int siiOnlyCount = Math.max(0, siiTotalCount - matchedCount);

        return InvoiceContrastResultDto.builder()
                .periodo(effectivePeriodo)
                .queriedAt(LocalDateTime.now())
                .fromCache(fromCache)
                .siiTotalCount(siiTotalCount)
                .siiTotalAmount(siiTotalAmount)
                .localTotalCount(localInvoices.size())
                .localTotalAmount(localTotalAmount)
                .matchedCount(matchedCount)
                .pendingEmitCount(pendingAppointments.size())
                .siiOnlyCount(siiOnlyCount)
                .rawSiiResponse(siiResponse)
                .localInvoices(localInvoices)
                .pendingAppointments(pendingAppointments)
                .build();
    }
}
