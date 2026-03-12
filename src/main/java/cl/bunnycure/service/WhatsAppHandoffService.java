package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import cl.bunnycure.domain.model.BookingRequest;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WhatsAppHandoffService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AppSettingsService appSettingsService;

    public WhatsAppHandoffService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public String getHumanWhatsappNumber() {
        return normalizePhone(appSettingsService.getHumanWhatsappNumber());
    }

    public String buildHumanChannelLink() {
        return "https://wa.me/" + getHumanWhatsappNumber();
    }

    public String buildClientHandoffMessage() {
        String template = appSettingsService.getWhatsappHandoffClientMessage();
        return applyTokens(template, Map.of(
                "numero", formatForDisplay(getHumanWhatsappNumber())
        ));
    }

    public String buildAdminToCustomerLinkFromBookingRequest(BookingRequest request) {
        if (request == null) {
            return "";
        }
        String customerPhone = normalizePhone(request.getPhone());
        if (customerPhone.isBlank()) {
            return "";
        }

        String template = appSettingsService.getWhatsappHandoffAdminPrefill();
        String message = applyTokens(template, bookingRequestTokens(request));
        return buildWaMeLinkWithText(customerPhone, message);
    }

    public String buildAdminToCustomerLinkFromAppointment(Appointment appointment) {
        if (appointment == null || appointment.getCustomer() == null) {
            return "";
        }
        String customerPhone = normalizePhone(appointment.getCustomer().getPhone());
        if (customerPhone.isBlank()) {
            return "";
        }

        String template = appSettingsService.getWhatsappHandoffAdminPrefill();
        String message = applyTokens(template, appointmentTokens(appointment));
        return buildWaMeLinkWithText(customerPhone, message);
    }

    public String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return "";
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits;
    }

    private String buildWaMeLinkWithText(String normalizedPhone, String message) {
        return "https://wa.me/" + normalizedPhone + "?text=" + urlEncode(message);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private String formatForDisplay(String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return "";
        }
        return normalizedPhone.startsWith("+") ? normalizedPhone : "+" + normalizedPhone;
    }

    private String applyTokens(String template, Map<String, String> tokens) {
        String resolved = template != null ? template : "";
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return resolved;
    }

    private Map<String, String> bookingRequestTokens(BookingRequest request) {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("nombre", safe(request.getFullName()));
        tokens.put("telefono", formatForDisplay(normalizePhone(request.getPhone())));
        tokens.put("servicio", request.getService() != null ? safe(request.getService().getName()) : "");
        tokens.put("fecha", request.getPreferredDate() != null ? request.getPreferredDate().format(DATE_FORMAT) : "");
        tokens.put("bloque", safe(request.getPreferredBlock()));
        return tokens;
    }

    private Map<String, String> appointmentTokens(Appointment appointment) {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("nombre", safe(appointment.getCustomer().getFullName()));
        tokens.put("telefono", formatForDisplay(normalizePhone(appointment.getCustomer().getPhone())));
        tokens.put("servicio", appointment.getService() != null ? safe(appointment.getService().getName()) : "");
        tokens.put("fecha", appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().format(DATE_FORMAT) : "");
        tokens.put("hora", appointment.getAppointmentTime() != null ? appointment.getAppointmentTime().format(TIME_FORMAT) : "");
        return tokens;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
