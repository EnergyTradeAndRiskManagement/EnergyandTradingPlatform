package com.Trading.tradeservice.cache;

import com.Trading.tradeservice.dtos.Response.CommodityResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class commudityCache {

    private static final String CACHE_PREFIX = "commodity:";
    private final RedisTemplate<String, CommodityResponse> redisTemplate;

    public commudityCache(RedisTemplate<String, CommodityResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(CommodityResponse commodity) {

        String key = CACHE_PREFIX + commodity.getCode();

        redisTemplate.opsForValue().set(
                key,
                commodity,
                Duration.ofHours(24)
        );


    }
    public CommodityResponse get(String code) {

        String key = CACHE_PREFIX + code;

        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String code) {

        String key = CACHE_PREFIX + code;

        redisTemplate.delete(key);
    }
}
