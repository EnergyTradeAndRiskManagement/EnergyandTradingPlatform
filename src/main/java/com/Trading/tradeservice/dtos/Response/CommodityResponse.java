package com.Trading.tradeservice.dtos.Response;


import lombok.Data;

@Data
public class CommodityResponse {
    private Long id;
    private String name;
    private String code;
    private String unit;
    private String description;
}
