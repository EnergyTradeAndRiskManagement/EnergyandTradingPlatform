package com.Trading.tradeservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeConfirmationEvent {
    private String eventId; // Unique message ID for idempotency deduplication
    private Long tradeId;
    private String confirmationStatus; // "CONFIRMED", "SETTLED", "REJECTED"
    private String clearingReference;
    private String remarks;
    private LocalDateTime confirmationTime;
}
