package com.Trading.tradeservice.dtos.Request;


import com.Trading.tradeservice.models.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TradeRequest {

    @NotNull(message = "Trade type is required")
    private TradeType trade_type;

    @NotNull(message = "Commodity is required")
    private String commodity;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.00000001", message = "Quantity must be greater than 0")
    private Double quantity;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    private Double price;

    @NotNull(message = "Currency is required")
    private String currency;

    @NotNull(message = "Counterparty ID is required")
    private Long counterparty_id;

    @NotNull(message = "Location is required")
    private String location;

    @NotNull(message = "Trade date is required")
    private LocalDate TradeDate;


    private Long Version;

}