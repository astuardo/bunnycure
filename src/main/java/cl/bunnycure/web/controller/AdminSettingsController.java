package cl.bunnycure.web.controller;

import cl.bunnycure.service.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AppSettingsService settingsService;

    @GetMapping
    public String index(Model model) {
        // ── Branding & Identidad (Fase 1) ───────────────────────────────────
        model.addAttribute("appName",           settingsService.getAppName());
        model.addAttribute("appSlogan",         settingsService.getAppSlogan());
        model.addAttribute("appEmail",          settingsService.getAppEmail());
        model.addAttribute("appLogoUrl",        settingsService.getAppLogoUrl());
        model.addAttribute("appWebsiteUrl",     settingsService.getAppWebsiteUrl());
        model.addAttribute("appInstagramUrl",   settingsService.getAppInstagramUrl());
        model.addAttribute("appInstagramHandle", settingsService.getAppInstagramHandle());
        model.addAttribute("appPhoneDisplay",   settingsService.getAppPhoneDisplay());
        model.addAttribute("appOwnerName",      settingsService.getAppOwnerName());
        model.addAttribute("appPrimaryColor",   settingsService.getAppPrimaryColor());
        model.addAttribute("appSecondaryColor", settingsService.getAppSecondaryColor());
        model.addAttribute("appTimezone",       settingsService.getAppTimezone());
        model.addAttribute("appLocale",         settingsService.getAppLocale());
        model.addAttribute("appCurrency",       settingsService.getAppCurrency());
        model.addAttribute("appServiceTip",     settingsService.getAppServiceTip());

        // ── Configuración de Reservas ───────────────────────────────────────
        model.addAttribute("bookingEnabled",   settingsService.isBookingEnabled());
        model.addAttribute("whatsappEnabled",  settingsService.isWhatsappEnabled());
        model.addAttribute("whatsappNumber",   settingsService.getWhatsappNumber());
        model.addAttribute("whatsappHumanNumber", settingsService.getHumanWhatsappNumber());
        model.addAttribute("whatsappAdminAlertNumber", settingsService.getAdminAlertWhatsappNumber("56964499995"));
        model.addAttribute("whatsappAdminAlertEnabled", settingsService.isWhatsappAdminAlertEnabled());
        model.addAttribute("whatsappHumanDisplayName", settingsService.getHumanWhatsappDisplayName());
        model.addAttribute("whatsappHandoffEnabled", settingsService.isWhatsappHandoffEnabled());
        model.addAttribute("mailEnabled", settingsService.isMailEnabled());
        model.addAttribute("waTemplateConfirmationName", settingsService.get("whatsapp.template.confirmation.name", "confirmacion_cita"));
        model.addAttribute("waTemplateReminderName", settingsService.get("whatsapp.template.reminder.name", "recordatorio_cita"));
        model.addAttribute("waTemplateCancellationName", settingsService.get("whatsapp.template.cancellation.name", "cancelacion_cita"));
        model.addAttribute("waTemplateBookingReviewName", settingsService.get("whatsapp.template.booking-review.name", "agenda_en_revision"));
        model.addAttribute("waTemplateBookingRejectedName", settingsService.get("whatsapp.template.booking-rejected.name", "solicitud_rechazada"));
        model.addAttribute("waTemplateAdminAlertName", settingsService.get("whatsapp.template.admin-alert.name", ""));
        model.addAttribute("waTemplateAdminAppointmentAlertName", settingsService.get("whatsapp.template.admin-appointment-alert.name", "confirmacion_hora"));
        model.addAttribute("waTemplateLanguage", settingsService.get("whatsapp.template.language", "es_CL"));
        model.addAttribute("waTemplateAdminAlertLanguage", settingsService.get("whatsapp.template.admin-alert.language", "es_CL"));
        model.addAttribute("waTemplateConfirmationEnabled", settingsService.getBoolean("whatsapp.template.confirmation.enabled", true));
        model.addAttribute("waTemplateReminderEnabled", settingsService.getBoolean("whatsapp.template.reminder.enabled", true));
        model.addAttribute("waTemplateCancellationEnabled", settingsService.getBoolean("whatsapp.template.cancellation.enabled", true));
        model.addAttribute("waTemplateBookingReviewEnabled", settingsService.getBoolean("whatsapp.template.booking-review.enabled", true));
        model.addAttribute("waTemplateBookingRejectedEnabled", settingsService.getBoolean("whatsapp.template.booking-rejected.enabled", true));
        model.addAttribute("waTemplateAdminAlertEnabled", settingsService.getBoolean("whatsapp.template.admin-alert.enabled", false));
        model.addAttribute("waTemplateAdminAppointmentAlertEnabled", settingsService.getBoolean("whatsapp.template.admin-appointment-alert.enabled", true));
        model.addAttribute("waAdminBookingRequestsUrl", settingsService.get("whatsapp.admin.booking-requests.url", ""));
        model.addAttribute("waBusinessName", settingsService.get("whatsapp.business.name", settingsService.getAppName()));
        model.addAttribute("whatsappHandoffClientMessage", settingsService.getWhatsappHandoffClientMessage());
        model.addAttribute("whatsappHandoffAdminPrefill", settingsService.getWhatsappHandoffAdminPrefill());
        model.addAttribute("msgTemplate",      settingsService.getBookingMessageTemplate());
        model.addAttribute("morningBlock",     settingsService.getMorningBlock());
        model.addAttribute("afternoonBlock",   settingsService.getAfternoonBlock());
        model.addAttribute("nightBlock",       settingsService.getNightBlock());
        model.addAttribute("morningEnabled",   settingsService.isMorningEnabled());
        model.addAttribute("afternoonEnabled", settingsService.isAfternoonEnabled());
        model.addAttribute("nightEnabled",     settingsService.isNightEnabled());
        model.addAttribute("reminderStrategy", settingsService.getReminderStrategy());
        model.addAttribute("reminderTwoHoursIntervalMinutes", settingsService.getReminderTwoHoursIntervalMinutes());
        model.addAttribute("notificationDefaultTitle", settingsService.getNotificationDefaultTitle());
        model.addAttribute("notificationDefaultBody", settingsService.getNotificationDefaultBody());
        model.addAttribute("notificationTwoHourTitle", settingsService.getNotificationTwoHourTitle());
        model.addAttribute("notificationTwoHourBody", settingsService.getNotificationTwoHourBody());
        model.addAttribute("reminderStrategyOptions", java.util.List.of(
                java.util.Map.entry("2hours",     "Solo 2 horas antes de la cita"),
                java.util.Map.entry("morning",    "Solo aviso mañana (08:00 del día de la cita)"),
                java.util.Map.entry("day_before", "Solo aviso día anterior (09:00)"),
                java.util.Map.entry("both",       "Mañana del día + 2 horas antes")
        ));

        // ── Configuración de Campos Dinámicos (Fase 3) ──────────────────────
        model.addAttribute("fieldEmailMode",          settingsService.getFieldEmailMode());
        model.addAttribute("fieldGenderMode",         settingsService.getFieldGenderMode());
        model.addAttribute("fieldBirthDateMode",      settingsService.getFieldBirthDateMode());
        model.addAttribute("fieldEmergencyPhoneMode", settingsService.getFieldEmergencyPhoneMode());
        model.addAttribute("fieldHealthNotesMode",    settingsService.getFieldHealthNotesMode());
        model.addAttribute("fieldGeneralNotesMode",   settingsService.getFieldGeneralNotesMode());
        model.addAttribute("fieldModeOptions", java.util.List.of(
                java.util.Map.entry("REQUIRED", "Obligatorio"),
                java.util.Map.entry("OPTIONAL", "Opcional"),
                java.util.Map.entry("HIDDEN",   "Oculto")
        ));

        return "admin/settings/index";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params,
                       RedirectAttributes ra) {
        String humanWhatsappNumber = params.getOrDefault("whatsappHumanNumber", params.getOrDefault("whatsappNumber", "56988873031"));
        String whatsappAdminAlertNumber = params.getOrDefault("whatsappAdminAlertNumber", "56964499995");
        
        // ── Guardar todas las configuraciones ────────────────────────────────
        settingsService.saveAll(Map.ofEntries(
                // Branding & Identidad (Fase 1)
                Map.entry("app.name", params.getOrDefault("appName", "BunnyCure")),
                Map.entry("app.slogan", params.getOrDefault("appSlogan", "Arte en tus manos ✨")),
                Map.entry("app.email", params.getOrDefault("appEmail", "contacto@bunnycure.cl")),
                Map.entry("app.logo-url", params.getOrDefault("appLogoUrl", "/images/logo.png")),
                Map.entry("app.website.url", params.getOrDefault("appWebsiteUrl", "https://www.bunnycure.cl")),
                Map.entry("app.instagram.url", params.getOrDefault("appInstagramUrl", "https://www.instagram.com/bunny.cure")),
                Map.entry("app.instagram.handle", params.getOrDefault("appInstagramHandle", "@bunny.cure")),
                Map.entry("app.phone.display", params.getOrDefault("appPhoneDisplay", "+56 9 6449 9995")),
                Map.entry("app.owner.name", params.getOrDefault("appOwnerName", "Dueña")),
                Map.entry("app.primary-color", params.getOrDefault("appPrimaryColor", "#F472B6")),
                Map.entry("app.secondary-color", params.getOrDefault("appSecondaryColor", "#8B5CF6")),
                Map.entry("app.timezone", params.getOrDefault("appTimezone", "America/Santiago")),
                Map.entry("app.locale", params.getOrDefault("appLocale", "es_CL")),
                Map.entry("app.currency", params.getOrDefault("appCurrency", "CLP")),
                Map.entry("app.service-tip", params.getOrDefault("appServiceTip", "Llega con las uñas limpias y sin esmalte")),
                
                // Configuración de Reservas
                Map.entry("booking.enabled", params.getOrDefault("bookingEnabled", "false")),
                Map.entry("whatsapp.enabled", params.getOrDefault("whatsappEnabled", "false")),
                Map.entry("whatsapp.number", humanWhatsappNumber),
                Map.entry("whatsapp.human.number", humanWhatsappNumber),
                Map.entry("whatsapp.admin-alert.number", whatsappAdminAlertNumber),
                Map.entry("whatsapp.admin-alert.enabled", params.getOrDefault("whatsappAdminAlertEnabled", "false")),
                Map.entry("whatsapp.human.display-name", params.getOrDefault("whatsappHumanDisplayName", "Equipo BunnyCure")),
                Map.entry("whatsapp.handoff.enabled", params.getOrDefault("whatsappHandoffEnabled", "false")),
                Map.entry("mail.enabled", params.getOrDefault("mailEnabled", "false")),
                Map.entry("whatsapp.template.confirmation.name", params.getOrDefault("waTemplateConfirmationName", "confirmacion_cita")),
                Map.entry("whatsapp.template.reminder.name", params.getOrDefault("waTemplateReminderName", "recordatorio_cita")),
                Map.entry("whatsapp.template.cancellation.name", params.getOrDefault("waTemplateCancellationName", "cancelacion_cita")),
                Map.entry("whatsapp.template.booking-review.name", params.getOrDefault("waTemplateBookingReviewName", "agenda_en_revision")),
                Map.entry("whatsapp.template.booking-rejected.name", params.getOrDefault("waTemplateBookingRejectedName", "solicitud_rechazada")),
                Map.entry("whatsapp.template.admin-alert.name", params.getOrDefault("waTemplateAdminAlertName", "")),
                Map.entry("whatsapp.template.admin-appointment-alert.name", params.getOrDefault("waTemplateAdminAppointmentAlertName", "confirmacion_hora")),
                Map.entry("whatsapp.template.language", params.getOrDefault("waTemplateLanguage", "es_CL")),
                Map.entry("whatsapp.template.admin-alert.language", params.getOrDefault("waTemplateAdminAlertLanguage", "es_CL")),
                Map.entry("whatsapp.template.confirmation.enabled", params.getOrDefault("waTemplateConfirmationEnabled", "false")),
                Map.entry("whatsapp.template.reminder.enabled", params.getOrDefault("waTemplateReminderEnabled", "false")),
                Map.entry("whatsapp.template.cancellation.enabled", params.getOrDefault("waTemplateCancellationEnabled", "false")),
                Map.entry("whatsapp.template.booking-review.enabled", params.getOrDefault("waTemplateBookingReviewEnabled", "false")),
                Map.entry("whatsapp.template.booking-rejected.enabled", params.getOrDefault("waTemplateBookingRejectedEnabled", "false")),
                Map.entry("whatsapp.template.admin-alert.enabled", params.getOrDefault("waTemplateAdminAlertEnabled", "false")),
                Map.entry("whatsapp.template.admin-appointment-alert.enabled", params.getOrDefault("waTemplateAdminAppointmentAlertEnabled", "false")),
                Map.entry("whatsapp.admin.booking-requests.url", params.getOrDefault("waAdminBookingRequestsUrl", "")),
                Map.entry("whatsapp.business.name", params.getOrDefault("waBusinessName", "BunnyCure")),
                Map.entry("whatsapp.handoff.client-message", params.getOrDefault("whatsappHandoffClientMessage", "")),
                Map.entry("whatsapp.handoff.admin-prefill", params.getOrDefault("whatsappHandoffAdminPrefill", "")),
                Map.entry("booking.message.template", params.getOrDefault("msgTemplate", "")),
                Map.entry("booking.block.morning", params.getOrDefault("morningBlock", "09:00 – 13:00")),
                Map.entry("booking.block.afternoon", params.getOrDefault("afternoonBlock", "15:00 – 18:00")),
                Map.entry("booking.block.night", params.getOrDefault("nightBlock", "19:00 – 22:00")),
                Map.entry("booking.block.morning.enabled", params.getOrDefault("morningEnabled", "false")),
                Map.entry("booking.block.afternoon.enabled", params.getOrDefault("afternoonEnabled", "false")),
                Map.entry("booking.block.night.enabled", params.getOrDefault("nightEnabled", "false")),
                Map.entry("reminder.strategy", params.getOrDefault("reminderStrategy", "2hours")),
                Map.entry("reminder.two-hours.interval-minutes", params.getOrDefault("reminderTwoHoursIntervalMinutes", "30")),
                Map.entry("notification.template.default.title", params.getOrDefault("notificationDefaultTitle", "Recordatorio de Cita")),
                Map.entry("notification.template.default.body", params.getOrDefault("notificationDefaultBody", "Hola {customerName}, tienes una cita de {serviceName} el {date} a las {time}.")),
                Map.entry("notification.template.2hour.title", params.getOrDefault("notificationTwoHourTitle", "¡Tu cita es pronto!")),
                Map.entry("notification.template.2hour.body", params.getOrDefault("notificationTwoHourBody", "Hola {customerName}, tu cita de {serviceName} es en {minutesUntil} minutos ({time}). ¡Te esperamos!")),

                // Campos Dinámicos (Fase 3)
                Map.entry("field.email.mode", params.getOrDefault("fieldEmailMode", "OPTIONAL")),
                Map.entry("field.gender.mode", params.getOrDefault("fieldGenderMode", "OPTIONAL")),
                Map.entry("field.birth-date.mode", params.getOrDefault("fieldBirthDateMode", "OPTIONAL")),
                Map.entry("field.emergency-phone.mode", params.getOrDefault("fieldEmergencyPhoneMode", "HIDDEN")),
                Map.entry("field.health-notes.mode", params.getOrDefault("fieldHealthNotesMode", "HIDDEN")),
                Map.entry("field.general-notes.mode", params.getOrDefault("fieldGeneralNotesMode", "OPTIONAL"))
        ));
        ra.addFlashAttribute("successMsg", "Configuración guardada ✅");
        return "redirect:/admin/settings";
    }
}
