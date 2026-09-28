package com.Trading.tradeservice.controllers;

import com.Trading.tradeservice.dtos.Request.TradeRequest;
import com.Trading.tradeservice.dtos.Response.TradeResponse;
import com.Trading.tradeservice.services.Trade.TradeService;
import com.Trading.tradeservice.validation.TradeValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@RestController
public class tradeControllers {
    private static final Logger log = LoggerFactory.getLogger(tradeControllers.class);

    private TradeService tradeService;
    private TradeValidator tradeValidator;

    public tradeControllers(TradeService tradeService, TradeValidator tradeValidator) {
        this.tradeService = tradeService;
        this.tradeValidator = tradeValidator;

    }

    @PreAuthorize("hasRole('TRADER')")
    @PostMapping("/api/v1/trades")
    public ResponseEntity<TradeResponse> createTrade(
            @RequestHeader("IdempotencyKey") String idempotencyKey,
            @RequestBody TradeRequest tradeRequest) throws JsonProcessingException {

        log.info("Creating trade, counterparty={}, product={}, quantity={}",
                tradeRequest.getTradeDate(),
                tradeRequest.getCommodity(),
                tradeRequest.getCounterparty_id());

        tradeService.captureTrade(idempotencyKey, tradeRequest);
        return ResponseEntity.ok(new TradeResponse());

        // Implementation for creating a trade
    }

    @PutMapping("/tradeId")
    public ResponseEntity<TradeResponse> updateTrade(
            @PathVariable Long tradeId,
            @RequestBody TradeRequest request){


           String result =  tradeService.updateTrade(tradeId, request);

        return ResponseEntity.ok(
                new TradeResponse()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<TradeResponse> getTrade(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                tradeService.getTrade(id)
        );
    }

    @GetMapping
    public ResponseEntity<List<TradeResponse>> getAllTrades() {

        return ResponseEntity.ok(
                tradeService.getAllTrades()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(
            @PathVariable Long id) {

        tradeService.deleteTrade(id);

        return ResponseEntity.noContent().build();
    }



}
