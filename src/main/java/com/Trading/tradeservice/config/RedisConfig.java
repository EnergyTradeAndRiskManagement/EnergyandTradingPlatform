package com.Trading.tradeservice.config;


import com.Trading.tradeservice.dtos.Response.CommodityResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, CommodityResponse> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, CommodityResponse> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        Jackson2JsonRedisSerializer<CommodityResponse> serializer = new Jackson2JsonRedisSerializer<>(
                        CommodityResponse.class
                );

        template.setKeySerializer(new StringRedisSerializer());

        template.setValueSerializer(serializer);

        template.setHashKeySerializer(new StringRedisSerializer());

        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }
}
