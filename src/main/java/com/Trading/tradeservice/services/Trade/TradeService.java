package com.Trading.tradeservice.services.Trade;

import com.Trading.tradeservice.Exceptions.IdempotencyException;
import com.Trading.tradeservice.Exceptions.InvalidTradeLifecycleException;
import com.Trading.tradeservice.Exceptions.TradeConflictException;
import com.Trading.tradeservice.Exceptions.TradeNotFoundException;
import com.Trading.tradeservice.dtos.Request.TradeRequest;
import com.Trading.tradeservice.dtos.Response.TradeResponse;
import com.Trading.tradeservice.events.tradeEvent;
import com.Trading.tradeservice.models.*;
import com.Trading.tradeservice.respositories.IdempotencyKeyRepository;
import com.Trading.tradeservice.respositories.TradeAuditRepository;
import com.Trading.tradeservice.respositories.Traderepository;
import com.Trading.tradeservice.validation.TradeValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class TradeService {

    private final TradeValidator tradeValidator;
    private final Traderepository tradeRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TradeAuditRepository tradeAuditRepository;
    private final KafkaTemplate<String, tradeEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TradeService(TradeValidator tradeValidator,
                        Traderepository tradeRepository,
                        IdempotencyKeyRepository idempotencyKeyRepository,
                        TradeAuditRepository tradeAuditRepository,
                        KafkaTemplate<String, tradeEvent> kafkaTemplate,
                        ObjectMapper objectMapper) {
        this.tradeValidator = tradeValidator;
        this.tradeRepository = tradeRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.tradeAuditRepository = tradeAuditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 1. CREATE TRADE (with Validation, Idempotency, Audit, and Kafka publishing)
     */
    public TradeResponse captureTrade(String idempotencyKey, TradeRequest tradeRequest) throws JsonProcessingException {
        // Validation
        tradeValidator.validate(idempotencyKey, tradeRequest);

        String currentRequestHash = calculateHash(tradeRequest);

        // Idempotency check
        Optional<IdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingKeyOpt.isPresent()) {
            IdempotencyKey existingKey = existingKeyOpt.get();
            if (!existingKey.getRequestHash().equals(currentRequestHash)) {
                throw new IdempotencyException("Idempotency key '" + idempotencyKey + "' already exists with different payload.");
            }
            log.info("Idempotent request detected for key: {}. Returning existing trade ID: {}", idempotencyKey, existingKey.getTradeId());
            if (existingKey.getTradeId() != null) {
                Trade existingTrade = tradeRepository.findById(existingKey.getTradeId())
                        .orElseThrow(() -> new TradeNotFoundException("Trade with ID " + existingKey.getTradeId() + " not found."));
                return mapToResponse(existingTrade);
            }
        }

        // Map and set initial lifecycle status
        Trade trade = mapToEntity(tradeRequest);
        trade.setStatus(TradeStatus.APPROVED.name()); // Default initial status
        trade.setUpdated_at(LocalDateTime.now());

        Trade savedTrade = tradeRepository.save(trade);

        // Save Idempotency Key
        IdempotencyKey newIdempotencyKey = new IdempotencyKey();
        newIdempotencyKey.setIdempotencyKey(idempotencyKey);
        newIdempotencyKey.setTradeId(savedTrade.getId());
        newIdempotencyKey.setStatusCode(IdempotencyStatus.COMPLETED);
        newIdempotencyKey.setRequestHash(currentRequestHash);
        newIdempotencyKey.setResponseBody(objectMapper.writeValueAsString(mapToResponse(savedTrade)));
        newIdempotencyKey.setCreatedAt(LocalDateTime.now());
        idempotencyKeyRepository.save(newIdempotencyKey);

        // Record Audit
        recordAudit(savedTrade.getId(), "CREATE", null, savedTrade.getStatus(), savedTrade.getVersion(),
                "Trade created: " + savedTrade.getTrade_type() + " " + savedTrade.getQuantity() + " "
                        + savedTrade.getCommodity() + " @ " + savedTrade.getPrice() + " at " + savedTrade.getLocation());

        // Publish to Kafka
        publishTradeEvent("trade-created", savedTrade);

        return mapToResponse(savedTrade);
    }

    /**
     * 2. GET TRADE (by ID)
     */
    @Transactional(readOnly = true)
    public TradeResponse getTrade(Long id) {
        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with ID: " + id));
        return mapToResponse(trade);
    }

    /**
     * 2. GET ALL TRADES (with optional status / commodity filter)
     */
    @Transactional(readOnly = true)
    public List<TradeResponse> getAllTrades() {
        return tradeRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * 3. UPDATE TRADE (with Validation, Optimistic Locking, Lifecycle Check, Audit, and Kafka event)
     */
    public TradeResponse updateTrade(Long tradeId, TradeRequest updateRequest) {
        // Validation
        tradeValidator.validateUpdate(updateRequest);

        Trade existingTrade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with ID: " + tradeId));

        // Lifecycle Check: Terminal states cannot be updated
        TradeStatus currentStatus = parseStatus(existingTrade.getStatus());
        if (currentStatus.isTerminal()) {
            throw new InvalidTradeLifecycleException("Cannot update trade in terminal state: " + currentStatus);
        }

        // Optimistic Locking Check
        if (updateRequest.getVersion() != null && !Objects.equals(existingTrade.getVersion(), updateRequest.getVersion())) {
            throw new TradeConflictException("Trade was modified concurrently. Current version is "
                    + existingTrade.getVersion() + ", provided was " + updateRequest.getVersion());
        }

        String oldDetails = summarizeTrade(existingTrade);

        // Apply updates to the managed entity
        existingTrade.setTrade_type(updateRequest.getTrade_type());
        existingTrade.setCommodity(updateRequest.getCommodity());
        existingTrade.setQuantity(updateRequest.getQuantity());
        existingTrade.setPrice(updateRequest.getPrice());
        existingTrade.setCurrency(updateRequest.getCurrency());
        existingTrade.setCounterparty_id(updateRequest.getCounterparty_id());
        existingTrade.setLocation(updateRequest.getLocation());
        existingTrade.setTradeDate(updateRequest.getTradeDate());
        existingTrade.setUpdated_at(LocalDateTime.now());

        Trade savedTrade = tradeRepository.save(existingTrade);

        // Record Audit
        recordAudit(savedTrade.getId(), "UPDATE", savedTrade.getStatus(), savedTrade.getStatus(), savedTrade.getVersion(),
                "Updated trade from [" + oldDetails + "] to [" + summarizeTrade(savedTrade) + "]");

        // Publish event for position service recalculation
        publishTradeEvent("trade-created", savedTrade);

        return mapToResponse(savedTrade);
    }

    /**
     * 4. CANCEL TRADE (Business cancellation instead of hard delete, with lifecycle check and audit)
     */
    public TradeResponse cancelTrade(Long tradeId, Long version, String reason) {
        Trade existingTrade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with ID: " + tradeId));

        TradeStatus currentStatus = parseStatus(existingTrade.getStatus());

        if (currentStatus == TradeStatus.CANCELLED) {
            throw new InvalidTradeLifecycleException("Trade is already CANCELLED.");
        }
        if (currentStatus == TradeStatus.SETTLED) {
            throw new InvalidTradeLifecycleException("Cannot cancel a SETTLED trade. Financial settlement has occurred.");
        }

        // Optimistic Locking Check
        if (version != null && !Objects.equals(existingTrade.getVersion(), version)) {
            throw new TradeConflictException("Trade was modified concurrently. Current version: "
                    + existingTrade.getVersion() + ", provided: " + version);
        }

        String oldStatus = existingTrade.getStatus();
        existingTrade.setStatus(TradeStatus.CANCELLED.name());
        existingTrade.setUpdated_at(LocalDateTime.now());

        Trade savedTrade = tradeRepository.save(existingTrade);

        // Record Audit
        recordAudit(savedTrade.getId(), "CANCEL", oldStatus, TradeStatus.CANCELLED.name(), savedTrade.getVersion(),
                "Trade cancelled. Reason: " + (reason != null ? reason : "User initiated cancellation"));

        // Notify Kafka
        publishTradeEvent("trade-created", savedTrade);

        return mapToResponse(savedTrade);
    }

    /**
     * 5. TRADE LIFECYCLE STATE TRANSITIONS
     */
    public TradeResponse updateTradeStatus(Long tradeId, TradeStatus newStatus, Long version) {
        Trade existingTrade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new TradeNotFoundException("Trade not found with ID: " + tradeId));

        TradeStatus currentStatus = parseStatus(existingTrade.getStatus());

        // Validate lifecycle transition rules
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidTradeLifecycleException("Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        // Optimistic Locking Check
        if (version != null && !Objects.equals(existingTrade.getVersion(), version)) {
            throw new TradeConflictException("Trade was modified concurrently. Current version: "
                    + existingTrade.getVersion() + ", provided: " + version);
        }

        String oldStatus = existingTrade.getStatus();
        existingTrade.setStatus(newStatus.name());
        existingTrade.setUpdated_at(LocalDateTime.now());

        Trade savedTrade = tradeRepository.save(existingTrade);

        // Record Audit
        recordAudit(savedTrade.getId(), "STATUS_CHANGE", oldStatus, newStatus.name(), savedTrade.getVersion(),
                "Status transitioned from " + oldStatus + " to " + newStatus.name());

        return mapToResponse(savedTrade);
    }

    /**
     * 8. AUDIT HISTORY RETRIEVAL
     */
    @Transactional(readOnly = true)
    public List<TradeAudit> getTradeAuditHistory(Long tradeId) {
        if (!tradeRepository.existsById(tradeId)) {
            throw new TradeNotFoundException("Trade not found with ID: " + tradeId);
        }
        return tradeAuditRepository.findByTradeIdOrderByTimestampDesc(tradeId);
    }

    // Helper: Record Audit Entry
    private void recordAudit(Long tradeId, String action, String oldStatus, String newStatus, Long version, String details) {
        String username = getCurrentUsername();
        TradeAudit audit = TradeAudit.builder()
                .tradeId(tradeId)
                .action(action)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .version(version)
                .modifiedBy(username)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
        tradeAuditRepository.save(audit);
    }

    // Helper: Current authenticated username
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "SYSTEM";
    }

    // Helper: Publish Kafka event to keep position-service synchronized
    private void publishTradeEvent(String topic, Trade trade) {
        try {
            tradeEvent event = new tradeEvent();
            event.setTradeId(trade.getId());
            event.setTradeType(trade.getTrade_type() != null ? trade.getTrade_type().name() : "BUY");
            event.setCommodity(trade.getCommodity());
            event.setQuantity(trade.getQuantity() != null ? trade.getQuantity() : 0.0);
            event.setPrice(trade.getPrice() != null ? trade.getPrice() : 0.0);
            event.setCurrency(trade.getCurrency());
            event.setCounterpartyId(trade.getCounterparty_id());
            event.setLocation(trade.getLocation() != null ? trade.getLocation() : "DEFAULT");
            event.setTradeDate(trade.getTradeDate() != null ? trade.getTradeDate() : java.time.LocalDate.now());
            event.setDeliveryDate(trade.getTradeDate() != null ? trade.getTradeDate() : java.time.LocalDate.now());

            kafkaTemplate.send(topic, String.valueOf(event.getTradeId()), event);
            log.info("Sent Kafka trade event for Trade ID: {} to topic: {}", trade.getId(), topic);
        } catch (Exception ex) {
            log.error("Failed to publish Kafka event for trade ID {}: {}", trade.getId(), ex.getMessage());
        }
    }

    private String calculateHash(TradeRequest tradeRequest) {
        return String.valueOf(Objects.hash(
                tradeRequest.getTrade_type(),
                tradeRequest.getCommodity(),
                tradeRequest.getQuantity(),
                tradeRequest.getPrice(),
                tradeRequest.getCurrency(),
                tradeRequest.getCounterparty_id(),
                tradeRequest.getLocation(),
                tradeRequest.getTradeDate()
        ));
    }

    private TradeStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) {
            return TradeStatus.DRAFT;
        }
        try {
            return TradeStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TradeStatus.DRAFT;
        }
    }

    private String summarizeTrade(Trade t) {
        return "Type=" + t.getTrade_type() + ", Commodity=" + t.getCommodity() + ", Qty=" + t.getQuantity()
                + ", Price=" + t.getPrice() + ", Location=" + t.getLocation();
    }

    private Trade mapToEntity(TradeRequest request) {
        Trade trade = new Trade();
        trade.setTrade_type(request.getTrade_type());
        trade.setCommodity(request.getCommodity());
        trade.setQuantity(request.getQuantity());
        trade.setPrice(request.getPrice());
        trade.setCurrency(request.getCurrency());
        trade.setCounterparty_id(request.getCounterparty_id());
        trade.setLocation(request.getLocation());
        trade.setTradeDate(request.getTradeDate());
        return trade;
    }

    private TradeResponse mapToResponse(Trade trade) {
        double notional = (trade.getQuantity() != null && trade.getPrice() != null)
                ? trade.getQuantity() * trade.getPrice() : 0.0;

        return TradeResponse.builder()
                .id(trade.getId())
                .trade_type(trade.getTrade_type())
                .commodity(trade.getCommodity())
                .quantity(trade.getQuantity())
                .price(trade.getPrice())
                .notional(Math.round(notional * 100.0) / 100.0)
                .currency(trade.getCurrency())
                .counterparty_id(trade.getCounterparty_id())
                .location(trade.getLocation())
                .status(trade.getStatus())
                .version(trade.getVersion())
                .tradeDate(trade.getTradeDate())
                .updated_at(trade.getUpdated_at())
                .build();
    }
}
