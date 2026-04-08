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
        private String number;
        private String humanNumber;
        private String adminAlertNumber;
        private String humanDisplayName;
        private Boolean handoffEnabled;
        private String handoffClientMessage;
        private String handoffAdminPrefill;
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
        private String defaultTitle;
        private String defaultBody;
        private String twoHourTitle;
        private String twoHourBody;
    }
}
