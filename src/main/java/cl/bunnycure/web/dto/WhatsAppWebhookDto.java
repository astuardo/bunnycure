package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO para recibir notificaciones de webhook de WhatsApp Cloud API
 * Documentacion: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookDto {

    private String object;
    private List<Entry> entry;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private List<Change> changes;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private Value value;
        private String field;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        @JsonProperty("messaging_product")
        private String messagingProduct;

        private Metadata metadata;
        private List<Contact> contacts;
        private List<Message> messages;
        private List<Status> statuses;
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        @JsonAnySetter
        public void setExtraField(String key, Object value) {
            if (key == null) {
                return;
            }
            if ("messaging_product".equals(key)
                    || "metadata".equals(key)
                    || "contacts".equals(key)
                    || "messages".equals(key)
                    || "statuses".equals(key)) {
                return;
            }
            extraFields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getExtraFields() {
            return extraFields;
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("display_phone_number")
        private String displayPhoneNumber;

        @JsonProperty("phone_number_id")
        private String phoneNumberId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        private Profile profile;

        @JsonProperty("wa_id")
        private String waId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String name;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String from;
        private String id;
        private String timestamp;
        private Text text;
        private String type;

        // Otros tipos de mensaje (image, video, document, audio, location, etc.)
        private Image image;
        private Video video;
        private Document document;
        private Audio audio;
        private Button button;
        private Interactive interactive;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Text {
        private String body;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private String id;

        @JsonProperty("mime_type")
        private String mimeType;

        private String caption;
        private String sha256;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Video {
        private String id;

        @JsonProperty("mime_type")
        private String mimeType;

        private String caption;
        private String sha256;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String id;

        @JsonProperty("mime_type")
        private String mimeType;

        private String filename;
        private String caption;
        private String sha256;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Audio {
        private String id;

        @JsonProperty("mime_type")
        private String mimeType;

        private String sha256;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Button {
        private String payload;
        private String text;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Interactive {
        private String type;

        @JsonProperty("button_reply")
        private ButtonReply buttonReply;

        @JsonProperty("list_reply")
        private ListReply listReply;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ButtonReply {
        private String id;
        private String title;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListReply {
        private String id;
        private String title;
        private String description;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String id;
        private String status;
        private String timestamp;

        @JsonProperty("recipient_id")
        private String recipientId;

        private Conversation conversation;
        private Pricing pricing;
        private List<StatusError> errors;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusError {
        private String code;
        private String title;
        private String message;
        private String href;

        @JsonProperty("error_data")
        private StatusErrorData errorData;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusErrorData {
        private String details;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conversation {
        private String id;
        private Origin origin;

        @JsonProperty("expiration_timestamp")
        private String expirationTimestamp;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Origin {
        private String type;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pricing {
        private boolean billable;

        @JsonProperty("pricing_model")
        private String pricingModel;

        private String category;
    }
}