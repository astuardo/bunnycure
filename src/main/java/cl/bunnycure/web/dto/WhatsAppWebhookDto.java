package cl.bunnycure.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO para recibir notificaciones de webhook de WhatsApp Cloud API
 * Documentación: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/components
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsAppWebhookDto {

    private String object;
    private List<Entry> entry;

    // Getters y Setters
    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<Entry> getEntry() {
        return entry;
    }

    public void setEntry(List<Entry> entry) {
        this.entry = entry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private String id;
        private List<Change> changes;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Change> getChanges() {
            return changes;
        }

        public void setChanges(List<Change> changes) {
            this.changes = changes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Change {
        private Value value;
        private String field;

        public Value getValue() {
            return value;
        }

        public void setValue(Value value) {
            this.value = value;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Value {
        @JsonProperty("messaging_product")
        private String messagingProduct;
        
        private Metadata metadata;
        private List<Contact> contacts;
        private List<Message> messages;
        private List<Status> statuses;
        private final Map<String, Object> extraFields = new LinkedHashMap<>();

        public String getMessagingProduct() {
            return messagingProduct;
        }

        public void setMessagingProduct(String messagingProduct) {
            this.messagingProduct = messagingProduct;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }

        public List<Contact> getContacts() {
            return contacts;
        }

        public void setContacts(List<Contact> contacts) {
            this.contacts = contacts;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }

        public List<Status> getStatuses() {
            return statuses;
        }

        public void setStatuses(List<Status> statuses) {
            this.statuses = statuses;
        }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        @JsonProperty("display_phone_number")
        private String displayPhoneNumber;
        
        @JsonProperty("phone_number_id")
        private String phoneNumberId;

        public String getDisplayPhoneNumber() {
            return displayPhoneNumber;
        }

        public void setDisplayPhoneNumber(String displayPhoneNumber) {
            this.displayPhoneNumber = displayPhoneNumber;
        }

        public String getPhoneNumberId() {
            return phoneNumberId;
        }

        public void setPhoneNumberId(String phoneNumberId) {
            this.phoneNumberId = phoneNumberId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        private Profile profile;
        
        @JsonProperty("wa_id")
        private String waId;

        public Profile getProfile() {
            return profile;
        }

        public void setProfile(Profile profile) {
            this.profile = profile;
        }

        public String getWaId() {
            return waId;
        }

        public void setWaId(String waId) {
            this.waId = waId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

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

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public Text getText() {
            return text;
        }

        public void setText(Text text) {
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Image getImage() {
            return image;
        }

        public void setImage(Image image) {
            this.image = image;
        }

        public Video getVideo() {
            return video;
        }

        public void setVideo(Video video) {
            this.video = video;
        }

        public Document getDocument() {
            return document;
        }

        public void setDocument(Document document) {
            this.document = document;
        }

        public Audio getAudio() {
            return audio;
        }

        public void setAudio(Audio audio) {
            this.audio = audio;
        }

        public Button getButton() {
            return button;
        }

        public void setButton(Button button) {
            this.button = button;
        }

        public Interactive getInteractive() {
            return interactive;
        }

        public void setInteractive(Interactive interactive) {
            this.interactive = interactive;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Text {
        private String body;

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private String id;
        @JsonProperty("mime_type")
        private String mimeType;
        private String caption;
        private String sha256;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Video {
        private String id;
        @JsonProperty("mime_type")
        private String mimeType;
        private String caption;
        private String sha256;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String id;
        @JsonProperty("mime_type")
        private String mimeType;
        private String filename;
        private String caption;
        private String sha256;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Audio {
        private String id;
        @JsonProperty("mime_type")
        private String mimeType;
        private String sha256;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Button {
        private String payload;
        private String text;

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Interactive {
        private String type;

        @JsonProperty("button_reply")
        private ButtonReply buttonReply;

        @JsonProperty("list_reply")
        private ListReply listReply;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public ButtonReply getButtonReply() {
            return buttonReply;
        }

        public void setButtonReply(ButtonReply buttonReply) {
            this.buttonReply = buttonReply;
        }

        public ListReply getListReply() {
            return listReply;
        }

        public void setListReply(ListReply listReply) {
            this.listReply = listReply;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ButtonReply {
        private String id;
        private String title;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ListReply {
        private String id;
        private String title;
        private String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        private String id;
        private String status;
        private String timestamp;
        
        @JsonProperty("recipient_id")
        private String recipientId;
        
        private Conversation conversation;
        private Pricing pricing;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getRecipientId() {
            return recipientId;
        }

        public void setRecipientId(String recipientId) {
            this.recipientId = recipientId;
        }

        public Conversation getConversation() {
            return conversation;
        }

        public void setConversation(Conversation conversation) {
            this.conversation = conversation;
        }

        public Pricing getPricing() {
            return pricing;
        }

        public void setPricing(Pricing pricing) {
            this.pricing = pricing;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Conversation {
        private String id;
        private Origin origin;
        
        @JsonProperty("expiration_timestamp")
        private String expirationTimestamp;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Origin getOrigin() {
            return origin;
        }

        public void setOrigin(Origin origin) {
            this.origin = origin;
        }

        public String getExpirationTimestamp() {
            return expirationTimestamp;
        }

        public void setExpirationTimestamp(String expirationTimestamp) {
            this.expirationTimestamp = expirationTimestamp;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Origin {
        private String type;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pricing {
        private boolean billable;
        
        @JsonProperty("pricing_model")
        private String pricingModel;
        
        private String category;

        public boolean isBillable() {
            return billable;
        }

        public void setBillable(boolean billable) {
            this.billable = billable;
        }

        public String getPricingModel() {
            return pricingModel;
        }

        public void setPricingModel(String pricingModel) {
            this.pricingModel = pricingModel;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}
