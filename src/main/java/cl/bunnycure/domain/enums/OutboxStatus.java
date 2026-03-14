package cl.bunnycure.domain.enums;

public enum OutboxStatus {
    PENDING,
    RETRY,
    SENT,
    FAILED
}
