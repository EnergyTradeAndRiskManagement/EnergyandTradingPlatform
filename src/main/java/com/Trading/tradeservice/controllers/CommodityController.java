package com.Trading.tradeservice.controllers;


import com.Trading.tradeservice.dtos.Request.CommodityRequest;
import com.Trading.tradeservice.dtos.Response.CommodityResponse;
import com.Trading.tradeservice.services.commudity.CommodityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
    @RequestMapping("/api/v1/commodities")
    public class CommodityController {

        private final CommodityService commodityService;

        public CommodityController(CommodityService commodityService) {
            this.commodityService = commodityService;
        }

        // CREATE
        @PostMapping
        public ResponseEntity<CommodityResponse> createCommodity(
                @Valid @RequestBody CommodityRequest request) {

            CommodityResponse response =
                    commodityService.createCommodity(request);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);
        }

        // GET BY ID
        @GetMapping("/{id}")
        public ResponseEntity<CommodityResponse> getCommodity(
                @PathVariable Long id) {

            return ResponseEntity.ok(
                    commodityService.getCommodity(id)
            );
        }

        // GET ALL
        @GetMapping
        public ResponseEntity<List<CommodityResponse>> getAllCommodities() {

            return ResponseEntity.ok(
                    commodityService.getAllCommodities()
            );
        }

        // UPDATE
        @PutMapping("/{id}")
        public ResponseEntity<CommodityResponse> updateCommodity(
                @PathVariable Long id,
                @Valid @RequestBody CommodityRequest request) {

            return ResponseEntity.ok(
                    commodityService.updateCommodity(id, request)
            );
        }

        // DELETE
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteCommodity(
                @PathVariable Long id) {

            commodityService.deleteCommodity(id);

            return ResponseEntity.noContent().build();
        }

            @GetMapping("/search")
            public ResponseEntity<Page<CommodityResponse>> searchCommodities(
                    @RequestParam(required = false, defaultValue = "") String search,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "5") int size) {

                return ResponseEntity.ok(
                        commodityService.searchCommodities(search, page, size)
                );
            }

    }

