package com.Trading.tradeservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name="Trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeType trade_type;
    private String commodity;

    @DecimalMin(value = "0.0001")
    private Double quantity;
    private Double price;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;
    private Long counterparty_id;
    private String location;
    private String status;


    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private LocalDate tradeDate;
    private LocalDateTime updated_at;

}
