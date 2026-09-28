package com.Trading.tradeservice.respositories;

import com.Trading.tradeservice.models.Commodity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommodityRepository extends JpaRepository<Commodity, Long> {
    boolean existsByCode(String code);
    Optional<Commodity> findByCode(String code);

    Page<Commodity> findByNameContainingIgnoreCase(String search, Pageable pageable);
}
