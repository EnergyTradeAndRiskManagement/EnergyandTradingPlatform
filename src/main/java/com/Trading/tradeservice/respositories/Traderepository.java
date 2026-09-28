package com.Trading.tradeservice.respositories;

import com.Trading.tradeservice.models.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Traderepository extends JpaRepository<Trade, Long> {

}
