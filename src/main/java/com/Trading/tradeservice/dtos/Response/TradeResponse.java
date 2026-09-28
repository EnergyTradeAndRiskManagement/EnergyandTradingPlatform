package com.Trading.tradeservice.dtos.Response;

import com.Trading.tradeservice.models.TradeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResponse {

    private Long id;
    private TradeType trade_type;
    private String commodity;
    private Double quantity;
    private Double price;
    private Double notional; // quantity * price
    private String currency;
    private Long counterparty_id;
    private String location;
    private String status;
    private Long version;
    private LocalDate tradeDate;
    private LocalDateTime updated_at;
}
