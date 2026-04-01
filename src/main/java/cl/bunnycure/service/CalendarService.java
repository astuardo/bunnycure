package cl.bunnycure.service;

import cl.bunnycure.domain.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Servicio para generar URLs y archivos de calendario.
 */
@Slf4j
@Service
public class CalendarService {

    private static final DateTimeFormatter GCAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    
    /**
     * Genera una URL de Google Calendar para agregar el evento.
     * 
     * @param appointment La cita
     * @return URL de Google Calendar
     */
    public String generateGoogleCalendarUrl(Appointment appointment) {
        try {
            LocalDateTime startDateTime = LocalDateTime.of(
                appointment.getAppointmentDate(), 
                appointment.getAppointmentTime()
            );
            
            // Duración estimada: 1 hora (puedes hacerlo configurable)
            LocalDateTime endDateTime = startDateTime.plusHours(1);
            
            // Formato requerido por Google Calendar: yyyyMMddTHHmmss
            String startFormatted = startDateTime.format(GCAL_DATE_FORMAT);
            String endFormatted = endDateTime.format(GCAL_DATE_FORMAT);
            
            String title = encode("Cita - " + appointment.getService().getName() + " - " + appointment.getCustomer().getFullName());
            String details = encode(buildEventDetails(appointment));
            String location = encode("BunnyCure");
            
            // URL de Google Calendar
            // https://calendar.google.com/calendar/render?action=TEMPLATE&text=TITLE&dates=START/END&details=DETAILS&location=LOCATION
            return String.format(
                "https://calendar.google.com/calendar/render?action=TEMPLATE&text=%s&dates=%s/%s&details=%s&location=%s&add=%s",
                title,
                startFormatted,
                endFormatted,
                details,
                location,
                encode(appointment.getCustomer().getEmail() != null ? appointment.getCustomer().getEmail() : "")
            );
        } catch (Exception e) {
            log.error("[CALENDAR] Error generando URL de Google Calendar: {}", e.getMessage());
            return "https://calendar.google.com";
        }
    }
    
    /**
     * Genera un contenido de archivo .ics (iCalendar) para descargar.
     * 
     * @param appointment La cita
     * @return Contenido del archivo .ics
     */
    public String generateICalendarFile(Appointment appointment) {
        LocalDateTime startDateTime = LocalDateTime.of(
            appointment.getAppointmentDate(), 
            appointment.getAppointmentTime()
        );
        
        // 1 hora antes para la alarma
        LocalDateTime alarmTime = startDateTime.minusHours(1);
        LocalDateTime endDateTime = startDateTime.plusHours(1);
        
        // Formato UTC para iCalendar
        DateTimeFormatter icsFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        ZoneId zone = ZoneId.of("America/Santiago");
        
        String startUtc = startDateTime.atZone(zone).withZoneSameInstant(ZoneId.of("UTC")).format(icsFormat);
        String endUtc = endDateTime.atZone(zone).withZoneSameInstant(ZoneId.of("UTC")).format(icsFormat);
        String now = LocalDateTime.now().atZone(zone).withZoneSameInstant(ZoneId.of("UTC")).format(icsFormat);
        
        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//BunnyCure//Appointment//ES\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(appointment.getId()).append("@bunnycure.cl\r\n");
        ics.append("DTSTAMP:").append(now).append("\r\n");
        ics.append("DTSTART:").append(startUtc).append("\r\n");
        ics.append("DTEND:").append(endUtc).append("\r\n");
        ics.append("SUMMARY:").append(escapeIcs(appointment.getService().getName() + " - " + appointment.getCustomer().getFullName())).append("\r\n");
        ics.append("DESCRIPTION:").append(escapeIcs(buildEventDetails(appointment))).append("\r\n");
        ics.append("LOCATION:").append(escapeIcs("BunnyCure")).append("\r\n");
        ics.append("STATUS:CONFIRMED\r\n");
        
        // Alarma 1 hora antes
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT1H\r\n");  // 1 hora antes
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Recordatorio: Cita en BunnyCure en 1 hora\r\n");
        ics.append("END:VALARM\r\n");
        
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");
        
        return ics.toString();
    }
    
    /**
     * Genera una URL corta de WhatsApp para contactar al cliente.
     * 
     * @param phoneNumber Número de teléfono del cliente
     * @return URL de WhatsApp
     */
    public String generateWhatsAppUrl(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        
        // Limpiar el número (quitar espacios, guiones, paréntesis)
        String cleanPhone = phoneNumber.replaceAll("[^0-9+]", "");
        
        // Si no tiene código de país, asumir Chile (+56)
        if (!cleanPhone.startsWith("+") && !cleanPhone.startsWith("56")) {
            cleanPhone = "56" + cleanPhone;
        }
        
        // Remover el + si existe
        cleanPhone = cleanPhone.replace("+", "");
        
        return "https://wa.me/" + cleanPhone;
    }
    
    private String buildEventDetails(Appointment appointment) {
        StringBuilder details = new StringBuilder();
        details.append("Cliente: ").append(appointment.getCustomer().getFullName()).append("\\n");
        details.append("Servicio: ").append(appointment.getService().getName()).append("\\n");
        details.append("Fecha: ").append(appointment.getAppointmentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\\n");
        details.append("Hora: ").append(appointment.getAppointmentTime().format(DateTimeFormatter.ofPattern("HH:mm"))).append("\\n");
        
        if (appointment.getObservations() != null && !appointment.getObservations().isBlank()) {
            details.append("\\nObservaciones: ").append(appointment.getObservations());
        }
        
        details.append("\\n\\nAgendado a través de BunnyCure");
        
        return details.toString();
    }
    
    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
    
    private String escapeIcs(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "");
    }
}
