package com.Trading.tradeservice.validation;

import com.Trading.tradeservice.Exceptions.TradeValidationException;
import com.Trading.tradeservice.dtos.Request.TradeRequest;
import com.Trading.tradeservice.models.TradeType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TradeValidator {

    public void validate(String idempotencyKey, TradeRequest trade) {
        validateIdempotencyKey(idempotencyKey);
        validateTradeFields(trade);
    }

    public void validateUpdate(TradeRequest trade) {
        validateTradeFields(trade);
        if (trade.getVersion() == null) {
            throw new TradeValidationException("Version is required for trade updates to guarantee optimistic locking");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new TradeValidationException("IdempotencyKey header is required for trade capture");
        }
    }

    private void validateTradeFields(TradeRequest trade) {
        if (trade == null) {
            throw new TradeValidationException("Trade request body cannot be null");
        }

        // Validate Trade Type
        if (trade.getTrade_type() == null) {
            throw new TradeValidationException("Trade type is required (BUY or SELL)");
        }

        // Validate Quantity
        if (trade.getQuantity() == null || trade.getQuantity() <= 0.00000001) {
            throw new TradeValidationException("Trade quantity must be greater than 0");
        }

        // Validate Price
        if (trade.getPrice() == null || trade.getPrice() < 0.0) {
            throw new TradeValidationException("Trade price cannot be negative");
        }

        // Validate Commodity
        if (trade.getCommodity() == null || trade.getCommodity().trim().isEmpty()) {
            throw new TradeValidationException("Commodity is required (e.g. CRUDE_OIL, NATURAL_GAS, POWER)");
        }

        // Validate Currency (ISO 4217 3-letter code)
        if (trade.getCurrency() == null || !trade.getCurrency().trim().matches("^[A-Z]{3}$")) {
            throw new TradeValidationException("Currency must be a valid 3-letter uppercase code (e.g. USD, EUR, GBP)");
        }

        // Validate Counterparty
        if (trade.getCounterparty_id() == null || trade.getCounterparty_id() <= 0) {
            throw new TradeValidationException("A valid positive Counterparty ID is required");
        }

        // Validate Location
        if (trade.getLocation() == null || trade.getLocation().trim().isEmpty()) {
            throw new TradeValidationException("Delivery location is required (e.g. HENRY_HUB, CUSHING, PJM)");
        }

        // Validate Trade Date
        if (trade.getTradeDate() == null) {
            throw new TradeValidationException("Trade date is required");
        }
        if (trade.getTradeDate().isAfter(LocalDate.now().plusDays(30))) {
            throw new TradeValidationException("Trade execution date cannot be more than 30 days in the future");
        }
    }
}
