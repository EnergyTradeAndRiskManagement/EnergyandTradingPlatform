package com.Trading.tradeservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentConsumerService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId, String consumerGroup) {
        return processedEventRepository.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markAsProcessed(String eventId, String consumerGroup, String topic, String payloadHash) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .topic(topic)
                .payloadHash(payloadHash)
                .processedAt(LocalDateTime.now())
                .build();
        processedEventRepository.save(processedEvent);
        log.info("Marked event ID '{}' as processed for consumer group '{}' on topic '{}'",
                eventId, consumerGroup, topic);
    }
}
