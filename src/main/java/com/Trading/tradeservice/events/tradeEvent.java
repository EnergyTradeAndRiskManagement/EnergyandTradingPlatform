package com.Trading.tradeservice.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class tradeEvent {
    private Long tradeId;
    private String tradeType; // BUY or SELL
    private String commodity;
    private double quantity;
    private double price;
    private String currency;
    private Long counterpartyId;
    private String location;
    private LocalDate tradeDate;
    private LocalDate deliveryDate;
}
