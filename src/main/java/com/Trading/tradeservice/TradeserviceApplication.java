package com.Trading.tradeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@EnableKafka
@SpringBootApplication
public class TradeserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeserviceApplication.class, args);
    }

}
