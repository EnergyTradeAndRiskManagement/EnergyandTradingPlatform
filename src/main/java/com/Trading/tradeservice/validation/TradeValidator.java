package com.Trading.tradeservice.validation;

import com.Trading.tradeservice.Exceptions.TradeValidationException;
import com.Trading.tradeservice.dtos.Request.TradeRequest;
import com.Trading.tradeservice.models.TradeType;
import com.Trading.tradeservice.respositories.CommodityRepository;
import com.Trading.tradeservice.respositories.CounterpartyRepository;
import com.Trading.tradeservice.respositories.CurrencyRepository;
import com.Trading.tradeservice.respositories.LocationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
@Service
public class TradeValidator {
    public static boolean isValidTradeType(String tradeType) {
        return tradeType.equalsIgnoreCase("BUY") || tradeType.equalsIgnoreCase("SELL");
    }


    private final CurrencyRepository currencyRepository;
    private final CommodityRepository commodityRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final LocationRepository locationRepository;


    public TradeValidator(CurrencyRepository currencyRepository, CommodityRepository commodityRepository,
                        CounterpartyRepository counterpartyRepository, LocationRepository locationRepository) {
        this.currencyRepository = currencyRepository;
        this.commodityRepository = commodityRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.locationRepository = locationRepository;
    }

    public void validate(String idempotencyKey, TradeRequest trade) {

        validateIdempotencyKey(idempotencyKey);
//        validateCurrency(trade.getCurrency());
//        validateCommodity(trade.getCommodity());
//        validateCounterparty(trade.getCounterparty_id());
//        validateLocation(trade.getLocation());
//        validateTradeDate(trade.getTradeDate());
//        validateTradeType(trade.getTrade_type());
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new TradeValidationException("Idempotency key header is required");
        }
    }

    private void validateCurrency(String currency) {

        if (!currencyRepository.existsByCode(currency)) {
            throw new TradeValidationException(
                    "Invalid currency: " + currency);
        }
    }

    private void validateCommodity(String commodity) {
        if (!commodityRepository.existsByCode(commodity)) {
            throw new TradeValidationException(
                    "Invalid commodity: " + commodity);
        }
    }

    private void validateCounterparty(Long counterpartyId) {
        if (!counterpartyRepository.existsById(counterpartyId)) {
            throw new TradeValidationException(
                    "Invalid counterparty ID: " + counterpartyId);
        }
    }

    private void validateLocation(String locationCode) {
        if (!locationRepository.existsByCode(locationCode)) {
            throw new TradeValidationException(
                    "Invalid location code: " + locationCode);
        }
    }

    private void validateTradeDate(LocalDate tradeDate) {

        if (tradeDate == null || tradeDate.isAfter(LocalDate.now())) {
            throw new TradeValidationException(
                    "Trade date cannot be in the future");
        }
    }
    private void validateTradeType(TradeType tradeType) {

        if (tradeType == null) {
            throw new TradeValidationException(
                    "Trade type is required");
        }
    }



}
