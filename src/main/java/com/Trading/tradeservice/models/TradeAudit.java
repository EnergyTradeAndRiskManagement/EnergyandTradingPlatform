package com.Trading.tradeservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tradeId;

    @Column(nullable = false)
    private String action; // CREATE, UPDATE, CANCEL, STATUS_CHANGE

    private String oldStatus;

    private String newStatus;

    @Column(length = 2000)
    private String details;

    private String modifiedBy;

    private Long version;

    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
