package com.Trading.tradeservice.models;

public enum TradeStatus {
    DRAFT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    SETTLEMENT_PENDING,
    SETTLED,
    CANCELLED;

    /**
     * Validates if the trade can transition from current state to the target state.
     */
    public boolean canTransitionTo(TradeStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == PENDING_REVIEW || target == APPROVED || target == CANCELLED;
            case PENDING_REVIEW -> target == APPROVED || target == REJECTED || target == CANCELLED;
            case APPROVED -> target == SETTLEMENT_PENDING || target == CANCELLED;
            case SETTLEMENT_PENDING -> target == SETTLED || target == CANCELLED;
            case REJECTED -> target == DRAFT;
            case SETTLED, CANCELLED -> false; // Terminal states cannot be altered
        };
    }

    public boolean isTerminal() {
        return this == SETTLED || this == CANCELLED;
    }
}
