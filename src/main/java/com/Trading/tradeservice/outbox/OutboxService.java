package com.Trading.tradeservice.outbox;

import com.Trading.tradeservice.events.tradeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Appends an event to the outbox table within the caller's active database transaction.
     */
    public OutboxEvent saveEvent(String aggregateType, Long aggregateId, String eventType, String topic, tradeEvent payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(jsonPayload)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            OutboxEvent saved = outboxRepository.save(outboxEvent);
            log.info("Saved Outbox event ID: {} [Type: {}, AggregateId: {}, Topic: {}]",
                    saved.getId(), eventType, aggregateId, topic);
            return saved;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trade event for Outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Outbox serialization failure", e);
        }
    }
}
