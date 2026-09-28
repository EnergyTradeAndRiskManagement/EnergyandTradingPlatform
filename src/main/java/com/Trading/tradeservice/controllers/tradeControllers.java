package com.Trading.tradeservice.controllers;

import com.Trading.tradeservice.dtos.Request.TradeRequest;
import com.Trading.tradeservice.dtos.Response.TradeResponse;
import com.Trading.tradeservice.models.TradeAudit;
import com.Trading.tradeservice.models.TradeStatus;
import com.Trading.tradeservice.services.Trade.TradeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/trades")
public class tradeControllers {

    private final TradeService tradeService;

    public tradeControllers(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    /**
     * 1. CREATE TRADE (with idempotency, validation, audit, and Kafka publishing)
     */
    @PreAuthorize("hasAnyRole('TRADER', 'ADMIN', 'USER')")
    @PostMapping
    public ResponseEntity<TradeResponse> createTrade(
            @RequestHeader(value = "IdempotencyKey", required = false) String idempotencyKey,
            @Valid @RequestBody TradeRequest tradeRequest) throws JsonProcessingException {

        log.info("Capturing trade, commodity={}, quantity={}, price={}, type={}",
                tradeRequest.getCommodity(), tradeRequest.getQuantity(), tradeRequest.getPrice(), tradeRequest.getTrade_type());

        TradeResponse response = tradeService.captureTrade(idempotencyKey, tradeRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. GET TRADE (by ID)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRADER', 'USER')")
    @GetMapping("/{id}")
    public ResponseEntity<TradeResponse> getTrade(@PathVariable Long id) {
        log.info("Fetching trade with ID: {}", id);
        return ResponseEntity.ok(tradeService.getTrade(id));
    }

    /**
     * 2. GET ALL TRADES
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRADER', 'USER')")
    @GetMapping
    public ResponseEntity<List<TradeResponse>> getAllTrades() {
        log.info("Fetching all trades");
        return ResponseEntity.ok(tradeService.getAllTrades());
    }

    /**
     * 3. UPDATE TRADE (with validation, optimistic locking, and audit)
     */
    @PreAuthorize("hasAnyRole('TRADER', 'ADMIN')")
    @PutMapping("/{tradeId}")
    public ResponseEntity<TradeResponse> updateTrade(
            @PathVariable Long tradeId,
            @Valid @RequestBody TradeRequest request) {

        log.info("Updating trade ID: {}, version: {}", tradeId, request.getVersion());
        TradeResponse updated = tradeService.updateTrade(tradeId, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 4. CANCEL TRADE (Business cancellation with audit trail)
     */
    @PreAuthorize("hasAnyRole('TRADER', 'ADMIN')")
    @PostMapping("/{tradeId}/cancel")
    public ResponseEntity<TradeResponse> cancelTrade(
            @PathVariable Long tradeId,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false, defaultValue = "User cancellation") String reason) {

        log.info("Cancelling trade ID: {}, version: {}, reason: {}", tradeId, version, reason);
        TradeResponse response = tradeService.cancelTrade(tradeId, version, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE endpoint aliased to cancelTrade (prevents physical data deletion)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRADER')")
    @DeleteMapping("/{tradeId}")
    public ResponseEntity<TradeResponse> deleteTrade(
            @PathVariable Long tradeId,
            @RequestParam(required = false) Long version) {

        log.info("Delete requested for trade ID: {}, executing soft cancellation", tradeId);
        TradeResponse response = tradeService.cancelTrade(tradeId, version, "Deleted via REST endpoint");
        return ResponseEntity.ok(response);
    }

    /**
     * 5. TRADE LIFECYCLE (State transition e.g., DRAFT -> PENDING_REVIEW -> APPROVED -> SETTLEMENT_PENDING -> SETTLED)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRADER')")
    @PostMapping("/{tradeId}/status")
    public ResponseEntity<TradeResponse> updateLifecycleStatus(
            @PathVariable Long tradeId,
            @RequestParam TradeStatus newStatus,
            @RequestParam(required = false) Long version) {

        log.info("Transitioning lifecycle status for trade ID: {} to {}", tradeId, newStatus);
        TradeResponse response = tradeService.updateTradeStatus(tradeId, newStatus, version);
        return ResponseEntity.ok(response);
    }

    /**
     * 8. AUDIT HISTORY (Full history of modifications, user, action, and versions)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TRADER', 'USER')")
    @GetMapping("/{tradeId}/audit")
    public ResponseEntity<List<TradeAudit>> getTradeAudit(@PathVariable Long tradeId) {
        log.info("Fetching audit trail for trade ID: {}", tradeId);
        List<TradeAudit> auditHistory = tradeService.getTradeAuditHistory(tradeId);
        return ResponseEntity.ok(auditHistory);
    }
}
