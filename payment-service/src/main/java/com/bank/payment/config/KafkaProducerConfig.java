package com.bank.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Value("${app.kafka.topics.payment-initiated:payment.initiated}")
    private String paymentInitiatedTopic;

    @Value("${app.kafka.topics.ledger-processed:ledger.processed}")
    private String ledgerProcessedTopic;

    @Bean
    public NewTopic paymentInitiatedTopic() {
        return TopicBuilder.name(paymentInitiatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ledgerProcessedTopic() {
        return TopicBuilder.name(ledgerProcessedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
