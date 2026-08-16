package cl.bunnycure.config;

import cl.bunnycure.service.AppointmentService;
import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private static final String TWO_HOUR_LAST_RUN_AT_KEY = "reminder.two-hours.last-run-at";

    private final AppointmentService appointmentService;
    private final AppSettingsService appSettingsService;
    private final cl.bunnycure.service.WebPushNotificationService webPushNotificationService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Recordatorio para citas de mañana.
     * Solo activo cuando reminder.strategy = "day_before".
     * Se ejecuta con cron/zone configurables via properties.
     */
    @Scheduled(
            cron = "${bunnycure.reminder.day-before.cron:0 0 9 * * *}",
            zone = "${bunnycure.scheduler.timezone:America/Santiago}"
    )
    public void sendDayBeforeReminders() {
        if (!appSettingsService.isReminderDayBeforeEnabled()) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio día anterior omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }
        try {
            log.info("[REMINDER-SCHEDULER] Iniciando recordatorios día anterior...");
            appointmentService.sendRemindersForUpcomingAppointments();
            log.info("[REMINDER-SCHEDULER] Recordatorios día anterior completados");
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Error en recordatorios día anterior", e);
        }
    }

    /**
     * Recordatorio para citas dentro de las próximas 2 horas.
     * Activo cuando reminder.strategy = "2hours" o "both" (default: "2hours").
     * Se ejecuta con cron/zone configurables via properties.
     * 
     * IMPORTANTE: Se ejecuta cada 30 minutos (no cada 2 horas) para detectar
     * citas agendadas recientemente. Ejemplo: si se agenda una cita a las 11:15
     * para las 12:00, el recordatorio se enviará en la siguiente ejecución 
     * (máximo 30 minutos de espera) en lugar de esperar hasta la próxima
     * ejecución de cada 2 horas.
     * 
     * El metodo verifica citas en ventana de 2h hacia adelante, pero se ejecuta
     * frecuentemente para no perder citas agendadas "last minute".
     */
    @Scheduled(cron = "0 * * * * *", zone = "${bunnycure.scheduler.timezone:America/Santiago}")
    public void sendTwoHourReminders() {
        if (!appSettingsService.isReminder2HoursEnabled()) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido (strategy={})",
                    appSettingsService.getReminderStrategy());
            return;
        }

        int intervalMinutes = appSettingsService.getReminderTwoHoursIntervalMinutes();
        if (!shouldRunTwoHourReminderNow(intervalMinutes)) {
            log.debug("[REMINDER-SCHEDULER] Recordatorio 2h omitido por frecuencia ({} min)", intervalMinutes);
            return;
        }

        try {
            log.info("[REMINDER-SCHEDULER] Iniciando recordatorios 2h...");
            appointmentService.sendRemindersForAppointmentsIn2Hours();
            appSettingsService.set(TWO_HOUR_LAST_RUN_AT_KEY, Instant.now().toString());
            log.info("[REMINDER-SCHEDULER] Recordatorios 2h completados");
        } catch (Exception e) {
            log.error("[REMINDER-SCHEDULER] Error en recordatorios 2h", e);
        }
    }

    private boolean shouldRunTwoHourReminderNow(int intervalMinutes) {
        String lastRunRaw = appSettingsService.get(TWO_HOUR_LAST_RUN_AT_KEY, "");
        if (lastRunRaw == null || lastRunRaw.isBlank()) {
            return true;
        }

        try {
            Instant lastRun = Instant.parse(lastRunRaw);
            long elapsedMinutes = Duration.between(lastRun, Instant.now()).toMinutes();
            return elapsedMinutes >= intervalMinutes;
        } catch (Exception ex) {
            log.warn("[REMINDER-SCHEDULER] Valor inválido en {}='{}', se ejecutará ahora",
                    TWO_HOUR_LAST_RUN_AT_KEY, lastRunRaw);
            return true;
        }
    }

    /**
     * Revisa periódicamente los bloqueos de agenda y días no laborables para enviar notificaciones Push PWA
     * 24 horas antes y 1 hora antes según la configuración del administrador.
     */
    @Scheduled(cron = "0 */10 * * * *", zone = "${bunnycure.scheduler.timezone:America/Santiago}")
    public void checkAndSendUnavailabilityPushReminders() {
        try {
            String notificationsRaw = appSettingsService.get("schedule.unavailability.notifications", "");
            boolean notify24h = true;
            boolean notify1h = true;
            boolean enabled = true;

            if (notificationsRaw != null && !notificationsRaw.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode notifNode = objectMapper.readTree(notificationsRaw);
                    if (notifNode.has("enabled") && !notifNode.get("enabled").asBoolean()) {
                        enabled = false;
                    }
                    if (notifNode.has("notify24HoursBefore")) {
                        notify24h = notifNode.get("notify24HoursBefore").asBoolean();
                    }
                    if (notifNode.has("notify1HourBefore")) {
                        notify1h = notifNode.get("notify1HourBefore").asBoolean();
                    }
                } catch (Exception ex) {
                    log.debug("[UNAVAILABILITY-PUSH] Error parseando config de notificaciones, usando defaults: {}", ex.getMessage());
                }
            }

            if (!enabled) {
                return;
            }

            String unavailabilitiesRaw = appSettingsService.get("schedule.unavailabilities", "");
            if (unavailabilitiesRaw == null || unavailabilitiesRaw.isBlank()) {
                return;
            }

            com.fasterxml.jackson.databind.JsonNode itemsNode = objectMapper.readTree(unavailabilitiesRaw);
            if (!itemsNode.isArray()) {
                return;
            }

            java.time.ZoneId zone = java.time.ZoneId.of(appSettingsService.get("app.timezone", "America/Santiago"));
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(zone);

            for (com.fasterxml.jackson.databind.JsonNode item : itemsNode) {
                String id = item.has("id") ? item.get("id").asText() : null;
                String startDateStr = item.has("startDate") ? item.get("startDate").asText() : null;
                String type = item.has("type") ? item.get("type").asText() : "FULL_DAY";
                String reason = item.has("reason") && !item.get("reason").asText().isBlank()
                        ? item.get("reason").asText()
                        : "Bloqueo de agenda";

                if (id == null || startDateStr == null) {
                    continue;
                }

                java.time.LocalDate startDate = java.time.LocalDate.parse(startDateStr);
                java.time.LocalTime startTime;
                if ("TIME_SLOT".equalsIgnoreCase(type) && item.has("startTime") && !item.get("startTime").asText().isBlank()) {
                    startTime = java.time.LocalTime.parse(item.get("startTime").asText());
                } else {
                    startTime = java.time.LocalTime.of(9, 0); // Inicio estándar para día completo
                }

                java.time.ZonedDateTime eventStart = java.time.ZonedDateTime.of(startDate, startTime, zone);
                long minutesUntilEvent = java.time.Duration.between(now, eventStart).toMinutes();

                // 1. Notificación 24 horas antes (ventana entre 23h y 25h = 1380 a 1500 minutos)
                if (notify24h && minutesUntilEvent >= 1380 && minutesUntilEvent <= 1500) {
                    String sentKey = "push.unavailability." + id + ".24h";
                    if (appSettingsService.get(sentKey, "").isBlank()) {
                        String title = "🗓️ Recordatorio: " + reason;
                        String body = "Tienes programado un bloqueo de agenda para mañana (" + startDateStr + (type.equals("TIME_SLOT") ? " a las " + startTime : "") + ").";
                        webPushNotificationService.sendAdminCustomNotification(title, body, "/calendar");
                        appSettingsService.set(sentKey, Instant.now().toString());
                        log.info("[UNAVAILABILITY-PUSH] Notificación 24h enviada para id={}", id);
                    }
                }

                // 2. Notificación 1 hora antes (ventana entre 45 y 75 minutos)
                if (notify1h && minutesUntilEvent >= 45 && minutesUntilEvent <= 75) {
                    String sentKey = "push.unavailability." + id + ".1h";
                    if (appSettingsService.get(sentKey, "").isBlank()) {
                        String title = "⏰ Recordatorio: " + reason;
                        String body = "Tienes programado un bloqueo de agenda en 1 hora (" + (type.equals("TIME_SLOT") ? startTime.toString() : "Día completo") + ").";
                        webPushNotificationService.sendAdminCustomNotification(title, body, "/calendar");
                        appSettingsService.set(sentKey, Instant.now().toString());
                        log.info("[UNAVAILABILITY-PUSH] Notificación 1h enviada para id={}", id);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[UNAVAILABILITY-PUSH] Error evaluando notificaciones de bloqueos", e);
        }
    }

}
