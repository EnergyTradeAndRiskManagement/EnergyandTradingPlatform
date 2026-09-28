package com.Trading.tradeservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_TRADE_CREATED = "trade-created";
    public static final String TOPIC_TRADE_CONFIRMATION = "trade-confirmation";

    @Bean
    public NewTopic tradeCreatedTopic() {
        return TopicBuilder.name(TOPIC_TRADE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic tradeConfirmationTopic() {
        return TopicBuilder.name(TOPIC_TRADE_CONFIRMATION)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
