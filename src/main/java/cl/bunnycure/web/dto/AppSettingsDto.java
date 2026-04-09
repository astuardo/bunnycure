package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para devolver todas las configuraciones del sistema agrupadas por secciones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppSettingsDto {
    
    private BrandingSettings branding;
    private WhatsAppSettings whatsapp;
    private BookingSettings booking;
    private ReminderSettings reminders;
    private FieldSettings fields;
    private NotificationTemplateSettings notificationTemplates;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrandingSettings {
        private String name;
        private String slogan;
        private String email;
        private String logoUrl;
        private String websiteUrl;
        private String instagramUrl;
        private String instagramHandle;
        private String phoneDisplay;
        private String ownerName;
        private String primaryColor;
        private String secondaryColor;
        private String timezone;
        private String locale;
        private String currency;
        private String serviceTip;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhatsAppSettings {
        private Boolean enabled;
        private String number;
        private String humanNumber;
        private String adminAlertNumber;
        private Boolean adminAlertEnabled;
        private String humanDisplayName;
        private Boolean handoffEnabled;
        private String handoffClientMessage;
        private String handoffAdminPrefill;
        private String templateConfirmationName;
        private String templateReminderName;
        private String templateCancellationName;
        private String templateBookingReviewName;
        private String templateBookingRejectedName;
        private String templateAdminAlertName;
        private String templateAdminAppointmentAlertName;
        private String templateLanguage;
        private String templateAdminAlertLanguage;
        private Boolean templateConfirmationEnabled;
        private Boolean templateReminderEnabled;
        private Boolean templateCancellationEnabled;
        private Boolean templateBookingReviewEnabled;
        private Boolean templateBookingRejectedEnabled;
        private Boolean templateAdminAlertEnabled;
        private Boolean templateAdminAppointmentAlertEnabled;
        private String adminBookingRequestsUrl;
        private String businessName;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingSettings {
        private Boolean enabled;
        private String messageTemplate;
        private BlockSettings morningBlock;
        private BlockSettings afternoonBlock;
        private BlockSettings nightBlock;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BlockSettings {
            private String timeRange;
            private Boolean enabled;
        }
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReminderSettings {
        private String strategy;
        private Integer twoHoursIntervalMinutes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldSettings {
        private String emailMode;
        private String genderMode;
        private String birthDateMode;
        private String emergencyPhoneMode;
        private String healthNotesMode;
        private String generalNotesMode;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationTemplateSettings {
        private Boolean emailEnabled;
        private String defaultTitle;
        private String defaultBody;
        private String twoHourTitle;
        private String twoHourBody;
    }
}
