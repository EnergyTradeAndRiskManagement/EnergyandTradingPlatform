package com.Trading.tradeservice.services.commudity;


import com.Trading.tradeservice.dtos.Request.CommodityRequest;
import com.Trading.tradeservice.dtos.Response.CommodityResponse;
import com.Trading.tradeservice.models.Commodity;
import com.Trading.tradeservice.respositories.CommodityRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommodityService {
    private final CommodityRepository commodityRepository;

    public CommodityService(CommodityRepository commodityRepository) {
        this.commodityRepository = commodityRepository;
    }

    public CommodityResponse createCommodity(CommodityRequest request) {

        if (commodityRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Commodity code already exists");
        }

        Commodity commodity = new Commodity();

        commodity.setName(request.getName());
        commodity.setCode(request.getCode());
        commodity.setUnit(request.getUnit());
        commodity.setDescription(request.getDescription());

        Commodity saved = commodityRepository.save(commodity);

        return mapToResponse(saved);
    }

    public CommodityResponse getCommodity(Long id) {

        Commodity commodity = commodityRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Commodity not found"));

        return mapToResponse(commodity);
    }

    @Cacheable(
            value = "commodities",
            key = "'active'"
    )
    @Transactional(readOnly = true)
    public List<CommodityResponse> getAllCommodities() {

        return commodityRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    @CacheEvict(
            value = "commodities",
            key = "'active'"
    )
    public CommodityResponse updateCommodity(
            Long id,
            CommodityRequest request) {

        Commodity commodity = commodityRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Commodity not found"));

        commodity.setName(request.getName());
        commodity.setCode(request.getCode());
        commodity.setUnit(request.getUnit());
        commodity.setDescription(request.getDescription());

        Commodity updated = commodityRepository.save(commodity);

        return mapToResponse(updated);
    }


    @CacheEvict(
            value = "commodities",
            key = "'active'"
    )
    public void deleteCommodity(Long id) {

        if (!commodityRepository.existsById(id)) {
            throw new RuntimeException("Commodity not found");
        }

        commodityRepository.deleteById(id);
    }



    private CommodityResponse mapToResponse(Commodity commodity) {

        CommodityResponse response = new CommodityResponse();

        response.setId(commodity.getId());
        response.setName(commodity.getName());
        response.setCode(commodity.getCode());
        response.setUnit(commodity.getUnit());
        response.setDescription(commodity.getDescription());

        return response;
    }

    public Page<CommodityResponse> searchCommodities(String search, int page, int size){
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Commodity> commodities = commodityRepository.findByNameContainingIgnoreCase(search, pageable);
        return commodities.map(this::mapToResponse);
    }

}
