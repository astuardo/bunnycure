package cl.bunnycure.domain.enums;

public enum BillingCycle {
    MONTHLY(1),
    ANNUAL(12);

    private final int months;

    BillingCycle(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }
}
