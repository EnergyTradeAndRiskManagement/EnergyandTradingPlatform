package com.Trading.tradeservice.events;

import lombok.Data;

@Data
public class tradeEvent {
    Long tradeId;
    String commodity;
    double quantity;
    double price;
}
