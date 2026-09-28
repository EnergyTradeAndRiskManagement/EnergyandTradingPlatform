package com.Trading.tradeservice.dtos.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommodityRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String code;

    @NotBlank
    private String unit;

    private String description;
}
