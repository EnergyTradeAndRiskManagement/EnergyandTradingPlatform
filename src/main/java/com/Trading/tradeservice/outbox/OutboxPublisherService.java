package com.Trading.tradeservice.outbox;

import com.Trading.tradeservice.events.tradeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, tradeEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    /**
     * Scheduled poller that processes pending outbox events with exponential resilience.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.interval-ms:2000}")
    public void processPendingOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox publisher found {} pending events to dispatch to Kafka", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    /**
     * Publishes a single outbox event to Kafka with retry accounting and status transition.
     */
    public void publishEvent(OutboxEvent event) {
        try {
            tradeEvent payload = objectMapper.readValue(event.getPayload(), tradeEvent.class);
            String partitionKey = String.valueOf(event.getAggregateId());

            kafkaTemplate.send(event.getTopic(), partitionKey, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            handlePublishFailure(event.getId(), ex.getMessage());
                        } else {
                            handlePublishSuccess(event.getId(), result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });

        } catch (Exception e) {
            log.error("Error preparing outbox event ID {} for publishing: {}", event.getId(), e.getMessage());
            handlePublishFailure(event.getId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePublishSuccess(Long eventId, String topic, int partition, long offset) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(LocalDateTime.now());
            event.setErrorMessage(null);
            outboxRepository.save(event);
            log.info("Outbox event ID {} successfully published to topic '{}' [p:{}, o:{}]",
                    eventId, topic, partition, offset);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePublishFailure(Long eventId, String error) {
        outboxRepository.findById(eventId).ifPresent(event -> {
            int retries = event.getRetryCount() + 1;
            event.setRetryCount(retries);
            event.setErrorMessage(error);

            if (retries >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event ID {} reached max retries ({}). Marked as FAILED. Error: {}",
                        eventId, maxRetries, error);
            } else {
                log.warn("Outbox event ID {} failed (retry {}/{}). Will retry on next run. Error: {}",
                        eventId, retries, maxRetries, error);
            }

            outboxRepository.save(event);
        });
    }
}
