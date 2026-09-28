package com.Trading.tradeservice.consumer;

import com.Trading.tradeservice.events.TradeConfirmationEvent;
import com.Trading.tradeservice.models.Trade;
import com.Trading.tradeservice.models.TradeAudit;
import com.Trading.tradeservice.models.TradeStatus;
import com.Trading.tradeservice.respositories.TradeAuditRepository;
import com.Trading.tradeservice.respositories.Traderepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeConfirmationConsumer {

    private static final String CONSUMER_GROUP = "energy-trading-group";
    private static final String TOPIC = "trade-confirmation";

    private final IdempotentConsumerService idempotentConsumerService;
    private final Traderepository tradeRepository;
    private final TradeAuditRepository tradeAuditRepository;

    /**
     * Idempotent Kafka Listener: Processes incoming confirmation/settlement events.
     * Guarantees that duplicate deliveries from Kafka do not cause duplicate state changes.
     */
    @Transactional
    @KafkaListener(
            topics = TOPIC,
            groupId = CONSUMER_GROUP,
            properties = {"spring.json.value.default.type=com.Trading.tradeservice.events.TradeConfirmationEvent"}
    )
    public void consumeConfirmation(TradeConfirmationEvent event) {
        String eventId = event.getEventId() != null
                ? event.getEventId()
                : "trade-confirm-" + event.getTradeId() + "-" + event.getConfirmationStatus();

        log.info("Received TradeConfirmationEvent for trade ID: {}, Event ID: {}, Status: {}",
                event.getTradeId(), eventId, event.getConfirmationStatus());

        // 1. Idempotency Check: Verify if this event ID was already processed
        if (idempotentConsumerService.isAlreadyProcessed(eventId, CONSUMER_GROUP)) {
            log.warn("DUPLICATE EVENT: Event ID '{}' already processed by group '{}'. Skipping to guarantee idempotency.",
                    eventId, CONSUMER_GROUP);
            return;
        }

        // 2. Execute business logic atomically
        tradeRepository.findById(event.getTradeId()).ifPresentOrElse(trade -> {
            String oldStatus = trade.getStatus();
            String newStatus = mapConfirmationToStatus(event.getConfirmationStatus());

            trade.setStatus(newStatus);
            trade.setUpdated_at(LocalDateTime.now());
            Trade saved = tradeRepository.save(trade);

            // Record audit trail
            TradeAudit audit = TradeAudit.builder()
                    .tradeId(saved.getId())
                    .action("CONFIRMATION_RECEIVED")
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .version(saved.getVersion())
                    .modifiedBy("KAFKA_CONFIRMATION_CONSUMER")
                    .details("Clearing ref: " + event.getClearingReference() + ". Remarks: " + event.getRemarks())
                    .timestamp(LocalDateTime.now())
                    .build();
            tradeAuditRepository.save(audit);

            log.info("Trade ID {} transitioned from {} to {} via Kafka confirmation event",
                    trade.getId(), oldStatus, newStatus);
        }, () -> log.error("Trade ID {} not found for confirmation event ID: {}", event.getTradeId(), eventId));

        // 3. Mark event as processed within the SAME database transaction
        idempotentConsumerService.markAsProcessed(eventId, CONSUMER_GROUP, TOPIC, String.valueOf(event.hashCode()));
    }

    private String mapConfirmationToStatus(String confirmationStatus) {
        if (confirmationStatus == null) return TradeStatus.APPROVED.name();
        return switch (confirmationStatus.toUpperCase()) {
            case "SETTLED" -> TradeStatus.SETTLED.name();
            case "REJECTED" -> TradeStatus.REJECTED.name();
            case "CONFIRMED" -> TradeStatus.APPROVED.name();
            default -> TradeStatus.SETTLEMENT_PENDING.name();
        };
    }
}
