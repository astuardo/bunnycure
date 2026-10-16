package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import cl.bunnycure.domain.model.Customer;
import cl.bunnycure.domain.model.LoyaltyReward;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servicio responsable de la lógica de formateo, textos y plantillas de WhatsApp.
 * Desacoplado de la infraestructura de transporte HTTP para alta cohesión y testabilidad.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppMessageBuilderService {

    private final AppSettingsService appSettingsService;

    /**
     * DTO inmutable que encapsula los parámetros de cabecera y cuerpo de una plantilla de WhatsApp.
     */
    public record TemplateParameters(String headerParam, List<String> bodyParams) {
    }

    public Locale resolveAppLocale() {
        try {
            Locale loc = appSettingsService != null ? appSettingsService.getAppJavaLocale() : null;
            return loc != null ? loc : Locale.forLanguageTag("es-CL");
        } catch (Exception ex) {
            log.warn("[WHATSAPP-BUILDER] No se pudo resolver app.locale, usando fallback es_CL", ex);
            return Locale.forLanguageTag("es-CL");
        }
    }

    public DateTimeFormatter shortDateFormatter() {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy", resolveAppLocale());
    }

    public DateTimeFormatter fullDateTimeFormatter() {
        return DateTimeFormatter.ofPattern("EEEE dd 'de' MMMM 'de' yyyy 'a las' HH:mm", resolveAppLocale());
    }

    public String safeValue(String value) {
        return (value == null || value.isBlank()) ? "-" : value.trim();
    }

    public String formatPreferredBlock(String rawBlock) {
        if (rawBlock == null || rawBlock.isBlank()) {
            return "(sin bloque)";
        }
        String normalized = rawBlock.trim();
        return switch (normalized.toUpperCase(Locale.ROOT)) {
            case "MORNING" -> "Manana";
            case "AFTERNOON" -> "Tarde";
            case "NIGHT" -> "Noche";
            default -> normalized;
        };
    }

    public String buildAdminReviewLink(Long bookingRequestId, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "Panel admin -> /admin/booking-requests";
        }
        String trimmed = baseUrl.trim();
        if (bookingRequestId == null) {
            return trimmed;
        }
        return trimmed.endsWith("/") ? trimmed + bookingRequestId : trimmed + "/" + bookingRequestId;
    }

    /**
     * Resuelve el motivo y origen de la cancelación a partir de las observaciones de la cita.
     */
    public String resolveCancellationReason(Appointment appointment) {
        if (appointment == null) {
            return "No especificado";
        }
        String obs = appointment.getObservations();
        if (obs == null || obs.isBlank()) {
            return "No especificado";
        }

        java.util.regex.Matcher motivoMatcher = java.util.regex.Pattern
                .compile("Motivo:\\s*(.+?)(\\n|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(obs);
        String reason = null;
        if (motivoMatcher.find()) {
            reason = motivoMatcher.group(1).trim();
        }

        java.util.regex.Matcher origenMatcher = java.util.regex.Pattern
                .compile("Cancelado por:\\s*(.+?)(\\n|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(obs);
        String origen = null;
        if (origenMatcher.find()) {
            origen = origenMatcher.group(1).trim();
        }

        String result;
        if (reason != null && !reason.isBlank()) {
            if (origen != null && !origen.isBlank()) {
                result = String.format("%s: %s", origen, reason);
            } else {
                result = reason;
            }
        } else if (origen != null && !origen.isBlank()) {
            result = String.format("Cancelado por %s", origen);
        } else {
            String firstLine = obs.split("\\n")[0].trim();
            result = firstLine.isBlank() ? "No especificado" : firstLine;
        }

        if (result.length() > 80) {
            return result.substring(0, 77) + "...";
        }
        return result;
    }

    public String buildAppointmentConfirmationText(Appointment appointment, String salonName) {
        String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                .format(fullDateTimeFormatter());

        return String.format(
                "💅 *Tu cita está confirmada - %s*\n\n" +
                        "Hola %s,\n\n" +
                        "Tu cita ha sido confirmada para el *%s*.\n\n" +
                        "📋 *Servicio:* %s\n" +
                        "⏱️ *Duración:* %d minutos\n\n" +
                        "⚠️ *Tolerancia:* Se esperará un máximo de 15 minutos o la cita se dará por cancelada.\n\n" +
                        "Te esperamos! ✨",
                salonName,
                appointment.getCustomer().getFullName(),
                fechaFormateada,
                appointment.getService().getName(),
                appointment.getService().getDurationMinutes()
        );
    }

    public String buildAppointmentCancellationText(Appointment appointment, String salonName) {
        String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                .format(fullDateTimeFormatter());

        return String.format(
                "❌ *Cita cancelada - %s*\n\n" +
                        "Hola %s,\n\n" +
                        "Tu cita del *%s* ha sido cancelada.\n\n" +
                        "Si deseas agendar una nueva cita, te invitamos a visitar nuestra web.\n\n" +
                        "Saludos cordiales.",
                salonName,
                appointment.getCustomer().getFullName(),
                fechaFormateada
        );
    }

    public String buildAppointmentReminderText(Appointment appointment, String salonName) {
        String fechaFormateada = LocalDateTime.of(appointment.getAppointmentDate(), appointment.getAppointmentTime())
                .format(fullDateTimeFormatter());

        return String.format(
                "⏰ *Recordatorio de tu cita - %s*\n\n" +
                        "Hola %s,\n\n" +
                        "Te recordamos que tienes una cita programada para el *%s*.\n\n" +
                        "📋 *Servicio:* %s\n\n" +
                        "⚠️ *Importante:* Recuerda llegar 5 minutos antes. La tolerancia máxima es de 15 minutos.\n\n" +
                        "Te esperamos! ✨",
                salonName,
                appointment.getCustomer().getFullName(),
                fechaFormateada,
                appointment.getService().getName()
        );
    }

    public String buildBookingRequestReceivedText(BookingRequest request, String salonName) {
        String fechaFormateada = request.getPreferredDate() != null
                ? request.getPreferredDate().format(shortDateFormatter())
                : "-";

        return String.format(
                "📋 *Solicitud de Cita Recibida - %s*\n\n" +
                        "Hola %s,\n\n" +
                        "Hemos recibido tu solicitud de cita para el *%s*.\n\n" +
                        "📋 *Servicio:* %s\n" +
                        "🕐 *Bloque preferido:* %s\n\n" +
                        "Estamos revisando la disponibilidad y te confirmaremos a la brevedad.\n\n" +
                        "¡Gracias por elegirnos! ✨",
                salonName,
                request.getFullName(),
                fechaFormateada,
                request.getService() != null ? request.getService().getName() : "-",
                formatPreferredBlock(request.getPreferredBlock())
        );
    }

    public String buildBookingRequestRejectedText(BookingRequest request, String salonName) {
        String fechaFormateada = request.getPreferredDate() != null
                ? request.getPreferredDate().format(shortDateFormatter())
                : "-";

        return String.format(
                "⚠️ *Información sobre tu solicitud - %s*\n\n" +
                        "Hola %s,\n\n" +
                        "Lamentamos informarte que no tenemos disponibilidad para tu solicitud del *%s* en el bloque *%s* (%s).\n\n" +
                        "Te invitamos a ingresar a nuestra página web para elegir otra fecha u horario disponible.\n\n" +
                        "¡Esperamos poder atenderte pronto! ✨",
                salonName,
                request.getFullName(),
                fechaFormateada,
                formatPreferredBlock(request.getPreferredBlock()),
                request.getService() != null ? request.getService().getName() : "-"
        );
    }

    public String buildAdminAlertText(BookingRequest request, String salonName, String reviewLink) {
        String serviceName = request.getService() != null && request.getService().getName() != null
                ? request.getService().getName()
                : "(sin servicio)";
        String preferredDate = request.getPreferredDate() != null
                ? request.getPreferredDate().format(shortDateFormatter())
                : "(sin fecha)";
        String preferredBlock = formatPreferredBlock(request.getPreferredBlock());
        String notes = request.getNotes() != null && !request.getNotes().isBlank()
                ? request.getNotes().trim()
                : "-";

        return String.format(
                "NUEVA SOLICITUD - %s\n\n" +
                        "%s solicitó una hora.\n" +
                        "Revisa disponibilidad en Solicitudes y confirma/reagenda.\n\n" +
                        "ID solicitud: %s\n" +
                        "Cliente: %s\n" +
                        "Telefono: %s\n" +
                        "Email: %s\n" +
                        "Servicio: %s\n" +
                        "Fecha preferida: %s\n" +
                        "Bloque: %s\n" +
                        "Notas: %s\n\n" +
                        "Revisar ahora: %s",
                salonName,
                safeValue(request.getFullName()),
                request.getId(),
                safeValue(request.getFullName()),
                safeValue(request.getPhone()),
                safeValue(request.getEmail()),
                serviceName,
                preferredDate,
                preferredBlock,
                notes,
                reviewLink
        );
    }

    public String buildLoyaltyUpdateText(Customer customer, LoyaltyReward reward) {
        int stamps = customer.getLoyaltyStamps() != null ? customer.getLoyaltyStamps() : 0;
        int maxStamps = 10;
        String rewardName = (reward != null) ? reward.getName() : "un premio especial";

        if (stamps == 0 && customer.getTotalCompletedVisits() > 0) {
            return String.format(
                    "🎉 *¡Felicidades %s!* 🎉\n\n" +
                            "Hoy has canjeado tu premio: *%s*.\n" +
                            "Tu tarjeta se ha reiniciado. ¡Sigue visitándonos para tu próximo regalo! 🐇✨",
                    customer.getFirstName(), rewardName
            );
        } else if (stamps >= maxStamps) {
            return String.format(
                    "🎉 *¡Felicidades %s!* 🎉\n\n" +
                            "Has completado tus 10 sellos.\n" +
                            "*¡Tu próxima cita es la del premio: %s!* 🎁✨\n" +
                            "Te esperamos para consentirte.",
                    customer.getFirstName(), rewardName
            );
        } else {
            return String.format(
                    "💖 *¡Gracias por tu visita!* 💖\n\n" +
                            "Acabas de ganar un nuevo sello.\n\n" +
                            "Llevas *%d/%d* sellos para tu premio: *%s*. 🐇✨",
                    stamps, maxStamps, rewardName
            );
        }
    }

    public TemplateParameters buildCitaConfirmadaParams(Appointment appointment, boolean dateChanged, boolean timeChanged, String loyaltyInfo) {
        var customer = appointment.getCustomer();
        String fecha = appointment.getAppointmentDate().format(shortDateFormatter());
        if (dateChanged) {
            fecha = fecha + " (Modificación)";
        }
        String hora = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        if (timeChanged) {
            hora = hora + " (Modificación)";
        }
        String servicio = appointment.getService().getName();
        String cliente = customer.getFullName();

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, hora, loyaltyInfo));
    }

    public TemplateParameters buildRecordatorioParams(Appointment appointment) {
        String fecha = appointment.getAppointmentDate().format(shortDateFormatter());
        String hora = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService().getName();
        String cliente = appointment.getCustomer().getFullName();

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, hora));
    }

    public TemplateParameters buildCancelacionParams(Appointment appointment, String motivo) {
        String fecha = appointment.getAppointmentDate().format(shortDateFormatter());
        String hora = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService() != null ? appointment.getService().getName() : "Servicio";
        String cliente = appointment.getCustomer().getFullName();

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, hora, motivo));
    }

    public TemplateParameters buildAgendaEnRevisionParams(BookingRequest request) {
        String fecha = request.getPreferredDate() != null ? request.getPreferredDate().format(shortDateFormatter()) : "-";
        String servicio = request.getService() != null ? request.getService().getName() : "-";
        String cliente = request.getFullName();
        String bloque = request.getPreferredBlock();

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, bloque));
    }

    public TemplateParameters buildSolicitudRechazadaParams(BookingRequest request) {
        String fecha = request.getPreferredDate() != null ? request.getPreferredDate().format(shortDateFormatter()) : "-";
        String servicio = request.getService() != null ? request.getService().getName() : "-";
        String cliente = request.getFullName();
        String bloque = request.getPreferredBlock();

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, bloque));
    }

    public TemplateParameters buildAdminBookingAlertParams(BookingRequest request) {
        String fecha = request.getPreferredDate() != null ? request.getPreferredDate().format(shortDateFormatter()) : "-";
        String servicio = request.getService() != null && request.getService().getName() != null ? request.getService().getName() : "-";
        String cliente = request.getFullName() != null && !request.getFullName().isBlank() ? request.getFullName().trim() : "-";
        String bloque = request.getPreferredBlock() != null && !request.getPreferredBlock().isBlank() ? request.getPreferredBlock().trim() : "-";

        return new TemplateParameters(cliente, Arrays.asList(servicio, fecha, bloque));
    }

    public TemplateParameters buildAdminAppointmentCreatedParams(Appointment appointment, String ownerName, String whatsappUrl, String calendarUrl) {
        String fecha = appointment.getAppointmentDate().format(shortDateFormatter());
        String hora = appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        String servicio = appointment.getService().getName();
        String cliente = appointment.getCustomer().getFullName();

        return new TemplateParameters(ownerName, Arrays.asList(cliente, servicio, fecha, hora, whatsappUrl, calendarUrl));
    }

    public Map<String, Object> buildTemplatePayload(
            String normalizedPhone,
            String templateName,
            String languageCode,
            String headerParam,
            List<String> bodyParams) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("to", normalizedPhone);
        payload.put("type", "template");

        Map<String, Object> template = new HashMap<>();
        template.put("name", templateName);

        Map<String, String> language = new HashMap<>();
        language.put("code", languageCode);
        template.put("language", language);

        List<Map<String, Object>> components = new ArrayList<>();

        if (headerParam != null && !headerParam.isBlank()) {
            Map<String, Object> header = new HashMap<>();
            header.put("type", "header");

            List<Map<String, String>> headerParameters = new ArrayList<>();
            Map<String, String> p = new HashMap<>();
            p.put("type", "text");
            p.put("text", headerParam);
            headerParameters.add(p);

            header.put("parameters", headerParameters);
            components.add(header);
        }

        if (bodyParams != null && !bodyParams.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "body");

            List<Map<String, String>> parameters = new ArrayList<>();
            for (String value : bodyParams) {
                Map<String, String> p = new HashMap<>();
                p.put("type", "text");
                p.put("text", value != null ? value : "");
                parameters.add(p);
            }

            body.put("parameters", parameters);
            components.add(body);
        }

        template.put("components", components);
        payload.put("template", template);

        return payload;
    }
}
