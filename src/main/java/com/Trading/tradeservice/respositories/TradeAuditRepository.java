package com.Trading.tradeservice.respositories;

import com.Trading.tradeservice.models.TradeAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeAuditRepository extends JpaRepository<TradeAudit, Long> {

    List<TradeAudit> findByTradeIdOrderByTimestampDesc(Long tradeId);

    List<TradeAudit> findByActionOrderByTimestampDesc(String action);
}
