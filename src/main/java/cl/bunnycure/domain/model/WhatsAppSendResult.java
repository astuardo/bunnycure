package cl.bunnycure.domain.model;

public record WhatsAppSendResult(boolean success, String wamid, String errorMessage) {
    public static WhatsAppSendResult ok(String wamid) {
        return new WhatsAppSendResult(true, wamid, null);
    }

    public static WhatsAppSendResult fail(String errorMessage) {
        return new WhatsAppSendResult(false, null, errorMessage);
    }
}
