package cl.bunnycure.web.controller;

import cl.bunnycure.config.WhatsAppConfig;
import cl.bunnycure.service.AppSettingsService;
import cl.bunnycure.service.WhatsAppService;
import cl.bunnycure.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API Controller para configuración del sistema.
 * Solo accesible para usuarios con rol ADMIN (configurado en SecurityConfig).
 */
@Slf4j
@Tag(name = "Settings", description = "API para configuración del sistema (solo ADMIN)")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsApiController {

    private final AppSettingsService settingsService;
    private final WhatsAppService whatsAppService;
    private final WhatsAppConfig whatsAppConfig;

    // Lista de claves válidas de configuración
    private static final Set<String> VALID_KEYS = new HashSet<>(java.util.Arrays.asList(
            "inventory.auto_consumption.enabled",
            // Branding & Identidad
            "app.name", "app.slogan", "app.email", "app.logo-url",
            "app.website.url", "app.instagram.url", "app.instagram.handle", "app.phone.display", "app.owner.name",
            "app.primary-color", "app.secondary-color",
            "app.timezone", "app.locale", "app.currency", "app.service-tip",
            // WhatsApp
            "whatsapp.enabled",
            "whatsapp.number", "whatsapp.human.number", "whatsapp.admin-alert.number",
            "whatsapp.admin-alert.enabled",
            "whatsapp.human.display-name",
            "whatsapp.handoff.enabled", "whatsapp.handoff.client-message", "whatsapp.handoff.admin-prefill",
            "whatsapp.template.confirmation.name", "whatsapp.template.reminder.name",
            "whatsapp.template.cancellation.name", "whatsapp.template.booking-review.name",
            "whatsapp.template.booking-rejected.name", "whatsapp.template.admin-alert.name",
            "whatsapp.template.admin-appointment-alert.name", "whatsapp.template.review.name", "whatsapp.template.language",
            "whatsapp.template.admin-alert.language", "whatsapp.template.confirmation.enabled",
            "whatsapp.template.reminder.enabled", "whatsapp.template.cancellation.enabled",
            "whatsapp.template.booking-review.enabled", "whatsapp.template.booking-rejected.enabled",
            "whatsapp.template.admin-alert.enabled", "whatsapp.template.admin-appointment-alert.enabled",
            "whatsapp.template.review.enabled",
            "whatsapp.admin.booking-requests.url", "whatsapp.business.name",
            // Mail
            "mail.enabled",
            // Booking
            "booking.enabled",
            "booking.message.template",
            "booking.block.morning", "booking.block.afternoon", "booking.block.night",
            "booking.block.morning.enabled", "booking.block.afternoon.enabled", "booking.block.night.enabled",
            // Reminders
            "reminder.strategy",
            "reminder.two-hours.interval-minutes",
            // Field Modes
            "field.email.mode", "field.gender.mode", "field.birth-date.mode",
            "field.emergency-phone.mode", "field.health-notes.mode", "field.general-notes.mode",
            // Notification Templates
            "notification.template.default.title", "notification.template.default.body",
            "notification.template.2hour.title", "notification.template.2hour.body",
            // GiftCard Template
            "giftcard.template.svg",
            // Schedule Unavailabilities & Blocks
            "schedule.unavailabilities", "schedule.unavailability.colors", "schedule.unavailability.notifications"
    ));

    private static final Set<String> VALID_REMINDER_STRATEGIES = Set.of(
            "2hours", "morning", "day_before", "both"
    );

    private static final Set<String> VALID_FIELD_MODES = Set.of(
            "REQUIRED", "OPTIONAL", "HIDDEN"
    );

    @Operation(
            summary = "Obtener todas las configuraciones",
            description = "Obtiene todas las configuraciones del sistema agrupadas por secciones.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuraciones obtenidas exitosamente",
                    content = @Content(schema = @Schema(implementation = AppSettingsDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<AppSettingsDto>> getAllSettings() {
        log.info("[API] Obteniendo todas las configuraciones del sistema");

        AppSettingsDto dto = AppSettingsDto.builder()
                .branding(getBrandingSettings())
                .whatsapp(getWhatsAppSettings())
                .booking(getBookingSettings())
                .reminders(getReminderSettings())
                .fields(getFieldSettings())
                .notificationTemplates(getNotificationTemplateSettings())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @Operation(
            summary = "Obtener configuración específica",
            description = "Obtiene el valor de una configuración específica por su clave.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuración encontrada"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Clave de configuración no válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSetting(
            @Parameter(description = "Clave de configuración", required = true)
            @PathVariable String key) {

        if (!VALID_KEYS.contains(key)) {
            log.warn("[API] Intento de acceder a clave inválida: {}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Clave de configuración no válida", "INVALID_KEY"));
        }

        String value = settingsService.get(key, "");
        log.info("[API] Obteniendo configuración: {} = {}", key, value);

        Map<String, String> result = Map.of(
                "key", key,
                "value", value != null ? value : ""
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Actualizar configuración",
            description = "Actualiza el valor de una configuración específica.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuración actualizada exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Valor inválido para la configuración",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Clave de configuración no válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateSetting(
            @Parameter(description = "Clave de configuración", required = true)
            @PathVariable String key,

            @Valid @RequestBody UpdateSettingRequest request) {

        if (!VALID_KEYS.contains(key)) {
            log.warn("[API] Intento de actualizar clave inválida: {}", key);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Clave de configuración no válida", "INVALID_KEY"));
        }

        // Validar valores según el tipo de configuración
        String validationError = validateSettingValue(key, request.getValue());
        if (validationError != null) {
            log.warn("[API] Validación fallida para {}: {}", key, validationError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(validationError, "INVALID_VALUE"));
        }

        log.info("[API] Actualizando configuración: {} = {}", key, request.getValue());
        settingsService.set(key, request.getValue());

        Map<String, String> result = Map.of(
                "key", key,
                "value", request.getValue()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Actualizar múltiples configuraciones",
            description = "Actualiza múltiples configuraciones en una sola operación.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuraciones actualizadas exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Una o más configuraciones contienen valores inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PutMapping("/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkUpdate(
            @Valid @RequestBody BulkUpdateSettingsRequest request) {

        Map<String, String> settings = request.getSettings();

        // Validar todas las claves primero
        List<String> invalidKeys = new ArrayList<>();
        for (String key : settings.keySet()) {
            if (!VALID_KEYS.contains(key)) {
                invalidKeys.add(key);
            }
        }

        if (!invalidKeys.isEmpty()) {
            log.warn("[API] Intento de actualización masiva con claves inválidas: {}", invalidKeys);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "Claves inválidas: " + String.join(", ", invalidKeys),
                            "INVALID_KEYS"
                    ));
        }

        // Validar valores
        Map<String, String> validationErrors = new HashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String error = validateSettingValue(entry.getKey(), entry.getValue());
            if (error != null) {
                validationErrors.put(entry.getKey(), error);
            }
        }

        if (!validationErrors.isEmpty()) {
            log.warn("[API] Validación fallida en actualización masiva: {}", validationErrors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "Valores inválidos en las configuraciones",
                            "VALIDATION_ERRORS"
                    ));
        }

        log.info("[API] Actualizando {} configuraciones en masa", settings.size());
        settingsService.saveAll(settings);

        Map<String, Object> result = Map.of(
                "updated", settings.size(),
                "keys", settings.keySet()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(
            summary = "Resetear a valores por defecto",
            description = "Resetea todas las configuraciones a sus valores por defecto.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Configuraciones reseteadas exitosamente"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado - solo ADMIN"
            )
    })
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<String>> resetToDefaults() {
        log.info("[API] Reseteando todas las configuraciones a valores por defecto");

        Map<String, String> defaults = createDefaultSettingsMap();
        settingsService.saveAll(defaults);

        return ResponseEntity.ok(ApiResponse.success("Configuraciones reseteadas a valores por defecto"));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Métodos privados auxiliares
    // ────────────────────────────────────────────────────────────────────────────

    private Map<String, String> createDefaultSettingsMap() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        // Branding & Identidad
        m.put("app.name", "BunnyCure");
        m.put("app.slogan", "Arte en tus manos 🐰");
        m.put("app.email", "contacto@bunnycure.cl");
        m.put("app.logo-url", "/images/logo.png");
        m.put("app.website.url", "https://www.bunnycure.cl");
        m.put("app.instagram.url", "https://www.instagram.com/bunny.cure");
        m.put("app.instagram.handle", "@bunny.cure");
        m.put("app.phone.display", "+56 9 6449 9995");
        m.put("app.owner.name", "Dueña");
        m.put("app.primary-color", "#F472B6");
        m.put("app.secondary-color", "#8B5CF6");
        m.put("app.timezone", "America/Santiago");
        m.put("app.locale", "es_CL");
        m.put("app.currency", "CLP");
        m.put("app.service-tip", "Llega con las uñas limpias y sin esmalte");

        // WhatsApp
        m.put("whatsapp.enabled", "true");
        m.put("whatsapp.number", "56988873031");
        m.put("whatsapp.human.number", "56988873031");
        m.put("whatsapp.admin-alert.number", "56964499995");
        m.put("whatsapp.admin-alert.enabled", "true");
        m.put("whatsapp.human.display-name", "Equipo BunnyCure");
        m.put("whatsapp.handoff.enabled", "true");
        m.put("whatsapp.handoff.client-message", "Si necesitas ayuda personalizada, escríbenos al WhatsApp de atención humana: {numero}.");
        m.put("whatsapp.handoff.admin-prefill", "Hola {nombre}, te escribe BunnyCure por tu solicitud o cita. Te contacto para ayudarte personalmente.");
        m.put("whatsapp.template.confirmation.name", "confirmacion_cita");
        m.put("whatsapp.template.reminder.name", "recordatorio_cita");
        m.put("whatsapp.template.cancellation.name", "cancelacion_cita");
        m.put("whatsapp.template.booking-review.name", "agenda_en_revision");
        m.put("whatsapp.template.booking-rejected.name", "solicitud_rechazada");
        m.put("whatsapp.template.admin-alert.name", "");
        m.put("whatsapp.template.admin-appointment-alert.name", "confirmacion_hora");
        m.put("whatsapp.template.review.name", "valoracion_servicio_google");
        m.put("whatsapp.template.language", "es_CL");
        m.put("whatsapp.template.admin-alert.language", "es_CL");
        m.put("whatsapp.template.confirmation.enabled", "true");
        m.put("whatsapp.template.reminder.enabled", "true");
        m.put("whatsapp.template.cancellation.enabled", "true");
        m.put("whatsapp.template.booking-review.enabled", "true");
        m.put("whatsapp.template.booking-rejected.enabled", "true");
        m.put("whatsapp.template.admin-alert.enabled", "false");
        m.put("whatsapp.template.admin-appointment-alert.enabled", "true");
        m.put("whatsapp.template.review.enabled", "true");
        m.put("whatsapp.admin.booking-requests.url", "");
        m.put("whatsapp.business.name", "BunnyCure");

        // Mail
        m.put("mail.enabled", "true");

        // Booking
        m.put("booking.enabled", "true");
        m.put("booking.message.template", "Hola Bunny Cure! 🐰\nMe gustaría reservar una cita:\n• Servicio: {servicio}\n• Fecha: {fecha}\n• Bloque: {bloque}\n• Nombre: {nombre}\n• Teléfono: {telefono}\n¿Tienen disponibilidad?");
        m.put("booking.block.morning", "09:00 - 13:00");
        m.put("booking.block.afternoon", "15:00 - 18:00");
        m.put("booking.block.night", "19:00 - 22:00");
        m.put("booking.block.morning.enabled", "true");
        m.put("booking.block.afternoon.enabled", "true");
        m.put("booking.block.night.enabled", "true");

        // Reminders
        m.put("reminder.strategy", "2hours");
        m.put("reminder.two-hours.interval-minutes", "30");

        // Field Modes
        m.put("field.email.mode", "OPTIONAL");
        m.put("field.gender.mode", "OPTIONAL");
        m.put("field.birth-date.mode", "OPTIONAL");
        m.put("field.emergency-phone.mode", "HIDDEN");
        m.put("field.health-notes.mode", "HIDDEN");
        m.put("field.general-notes.mode", "OPTIONAL");

        // Notification Templates
        m.put("notification.template.default.title", "Recordatorio de Cita");
        m.put("notification.template.default.body", "Hola {customerName}, tienes una cita de {serviceName} el {date} a las {time}.");
        m.put("notification.template.2hour.title", "¡Tu cita es pronto!");
        m.put("notification.template.2hour.body", "Hola {customerName}, tu cita de {serviceName} es en {minutesUntil} minutos ({time}). ¡Te esperamos!");

        return m;
    }

    private AppSettingsDto.BrandingSettings getBrandingSettings() {
        return AppSettingsDto.BrandingSettings.builder()
                .name(settingsService.getAppName())
                .slogan(settingsService.getAppSlogan())
                .email(settingsService.getAppEmail())
                .logoUrl(settingsService.getAppLogoUrl())
                .websiteUrl(settingsService.getAppWebsiteUrl())
                .instagramUrl(settingsService.getAppInstagramUrl())
                .instagramHandle(settingsService.getAppInstagramHandle())
                .phoneDisplay(settingsService.getAppPhoneDisplay())
                .ownerName(settingsService.getAppOwnerName())
                .primaryColor(settingsService.getAppPrimaryColor())
                .secondaryColor(settingsService.getAppSecondaryColor())
                .timezone(settingsService.getAppTimezone())
                .locale(settingsService.getAppLocale())
                .currency(settingsService.getAppCurrency())
                .serviceTip(settingsService.getAppServiceTip())
                .build();
    }

    private AppSettingsDto.WhatsAppSettings getWhatsAppSettings() {
        return AppSettingsDto.WhatsAppSettings.builder()
                .enabled(settingsService.isWhatsappEnabled())
                .number(settingsService.getWhatsappNumber())
                .humanNumber(settingsService.getHumanWhatsappNumber())
                .adminAlertNumber(settingsService.getAdminAlertWhatsappNumber("56964499995"))
                .adminAlertEnabled(settingsService.isWhatsappAdminAlertEnabled())
                .humanDisplayName(settingsService.getHumanWhatsappDisplayName())
                .handoffEnabled(settingsService.isWhatsappHandoffEnabled())
                .handoffClientMessage(settingsService.getWhatsappHandoffClientMessage())
                .handoffAdminPrefill(settingsService.getWhatsappHandoffAdminPrefill())
                .templateConfirmationName(settingsService.get("whatsapp.template.confirmation.name", "confirmacion_cita"))
                .templateReminderName(settingsService.get("whatsapp.template.reminder.name", "recordatorio_cita"))
                .templateCancellationName(settingsService.get("whatsapp.template.cancellation.name", "cancelacion_cita"))
                .templateBookingReviewName(settingsService.get("whatsapp.template.booking-review.name", "agenda_en_revision"))
                .templateBookingRejectedName(settingsService.get("whatsapp.template.booking-rejected.name", "solicitud_rechazada"))
                .templateAdminAlertName(settingsService.get("whatsapp.template.admin-alert.name", ""))
                .templateAdminAppointmentAlertName(settingsService.get("whatsapp.template.admin-appointment-alert.name", "confirmacion_hora"))
                .templateReviewName(settingsService.get("whatsapp.template.review.name", "valoracion_servicio_google"))
                .templateLanguage(settingsService.get("whatsapp.template.language", "es_CL"))
                .templateAdminAlertLanguage(settingsService.get("whatsapp.template.admin-alert.language", "es_CL"))
                .templateConfirmationEnabled(settingsService.getBoolean("whatsapp.template.confirmation.enabled", true))
                .templateReminderEnabled(settingsService.getBoolean("whatsapp.template.reminder.enabled", true))
                .templateCancellationEnabled(settingsService.getBoolean("whatsapp.template.cancellation.enabled", true))
                .templateBookingReviewEnabled(settingsService.getBoolean("whatsapp.template.booking-review.enabled", true))
                .templateBookingRejectedEnabled(settingsService.getBoolean("whatsapp.template.booking-rejected.enabled", true))
                .templateAdminAlertEnabled(settingsService.getBoolean("whatsapp.template.admin-alert.enabled", false))
                .templateAdminAppointmentAlertEnabled(settingsService.getBoolean("whatsapp.template.admin-appointment-alert.enabled", true))
                .templateReviewEnabled(settingsService.getBoolean("whatsapp.template.review.enabled", true))
                .adminBookingRequestsUrl(settingsService.get("whatsapp.admin.booking-requests.url", ""))
                .businessName(settingsService.get("whatsapp.business.name", settingsService.getAppName()))
                .build();
    }

    private AppSettingsDto.BookingSettings getBookingSettings() {
        return AppSettingsDto.BookingSettings.builder()
                .enabled(settingsService.isBookingEnabled())
                .messageTemplate(settingsService.getBookingMessageTemplate())
                .morningBlock(AppSettingsDto.BookingSettings.BlockSettings.builder()
                        .timeRange(settingsService.getMorningBlock())
                        .enabled(settingsService.isMorningEnabled())
                        .build())
                .afternoonBlock(AppSettingsDto.BookingSettings.BlockSettings.builder()
                        .timeRange(settingsService.getAfternoonBlock())
                        .enabled(settingsService.isAfternoonEnabled())
                        .build())
                .nightBlock(AppSettingsDto.BookingSettings.BlockSettings.builder()
                        .timeRange(settingsService.getNightBlock())
                        .enabled(settingsService.isNightEnabled())
                        .build())
                .build();
    }

    private AppSettingsDto.ReminderSettings getReminderSettings() {
        return AppSettingsDto.ReminderSettings.builder()
                .strategy(settingsService.getReminderStrategy())
                .twoHoursIntervalMinutes(settingsService.getReminderTwoHoursIntervalMinutes())
                .build();
    }

    private AppSettingsDto.FieldSettings getFieldSettings() {
        return AppSettingsDto.FieldSettings.builder()
                .emailMode(settingsService.getFieldEmailMode())
                .genderMode(settingsService.getFieldGenderMode())
                .birthDateMode(settingsService.getFieldBirthDateMode())
                .emergencyPhoneMode(settingsService.getFieldEmergencyPhoneMode())
                .healthNotesMode(settingsService.getFieldHealthNotesMode())
                .generalNotesMode(settingsService.getFieldGeneralNotesMode())
                .build();
    }

    private AppSettingsDto.NotificationTemplateSettings getNotificationTemplateSettings() {
        return AppSettingsDto.NotificationTemplateSettings.builder()
                .emailEnabled(settingsService.isMailEnabled())
                .defaultTitle(settingsService.getNotificationDefaultTitle())
                .defaultBody(settingsService.getNotificationDefaultBody())
                .twoHourTitle(settingsService.getNotificationTwoHourTitle())
                .twoHourBody(settingsService.getNotificationTwoHourBody())
                .build();
    }

    /**
     * Valida el valor de una configuración según su clave.
     * @return mensaje de error si es inválido, null si es válido
     */
    private String validateSettingValue(String key, String value) {
        Set<String> allowEmpty = Set.of(
                "whatsapp.template.admin-alert.name",
                "whatsapp.admin.booking-requests.url"
        );

        if (value == null || value.trim().isEmpty()) {
            if (allowEmpty.contains(key)) {
                return null;
            }
            return "El valor no puede estar vacío";
        }

        // Validar estrategia de recordatorios
        if (key.equals("reminder.strategy")) {
            if (!VALID_REMINDER_STRATEGIES.contains(value)) {
                return "Estrategia de recordatorio inválida. Valores válidos: " + VALID_REMINDER_STRATEGIES;
            }
        }

        if (key.equals("reminder.two-hours.interval-minutes")) {
            try {
                int minutes = Integer.parseInt(value);
                if (minutes < 5 || minutes > 120) {
                    return "Frecuencia inválida. Debe estar entre 5 y 120 minutos.";
                }
            } catch (NumberFormatException ex) {
                return "Frecuencia inválida. Debe ser un número entero.";
            }
        }

        // Validar modos de campos
        if (key.startsWith("field.") && key.endsWith(".mode")) {
            if (!VALID_FIELD_MODES.contains(value)) {
                return "Modo de campo inválido. Valores válidos: " + VALID_FIELD_MODES;
            }
        }

        // Validar valores booleanos
        if (key.equals("booking.enabled") ||
            key.equals("whatsapp.handoff.enabled") ||
            key.contains(".enabled")) {
            if (!value.equals("true") && !value.equals("false")) {
                return "Valor booleano inválido. Debe ser 'true' o 'false'";
            }
        }

        // Validar colores hexadecimales
        if (key.contains(".color")) {
            if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
                return "Color hexadecimal inválido. Formato esperado: #RRGGBB";
            }
        }

        return null; // válido
    }

    @Operation(summary = "Obtiene el estado completo de Meta WhatsApp (salud, plantillas y perfil)")
    @GetMapping("/whatsapp/meta-status")
    public ResponseEntity<Map<String, Object>> getWhatsAppMetaStatus() {
        Map<String, Object> response = new HashMap<>();
        String phoneId = whatsAppConfig.getPhoneId();
        String businessAccountId = whatsAppConfig.getBusinessAccountId();
        String token = whatsAppConfig.getToken();

        boolean configured = token != null && !token.isBlank() && phoneId != null && !phoneId.isBlank();
        response.put("configured", configured);
        response.put("phoneId", phoneId != null ? phoneId : "");
        response.put("businessAccountId", businessAccountId != null ? businessAccountId : "");
        response.put("apiVersion", "v22.0");

        if (configured) {
            whatsAppService.fetchPhoneNumberHealth(phoneId).ifPresent(health -> response.put("health", health));
            whatsAppService.fetchBusinessProfile(phoneId).ifPresent(profile -> response.put("businessProfile", profile));
            whatsAppService.fetchMessageTemplates(businessAccountId).ifPresent(templates -> response.put("templates", templates));
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Actualiza el perfil de negocio en Meta WhatsApp")
    @PutMapping("/whatsapp/business-profile")
    public ResponseEntity<Map<String, Object>> updateWhatsAppBusinessProfile(@RequestBody Map<String, Object> profileData) {
        boolean success = whatsAppService.updateBusinessProfile(profileData);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Perfil comercial actualizado en WhatsApp."));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "No se pudo actualizar el perfil comercial en Meta."));
    }
}
