package com.bank.ledger.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * Error handler that retries twice with a 1-second interval, and upon failure,
     * routes the poison-pill message to the Dead-Letter Topic (DLT/DLQ).
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumerRecord, exception) -> {
                    log.error("Routing failed message to Dead Letter Queue for key: {}, error: {}",
                            consumerRecord.key(), exception.getMessage());
                    return new TopicPartition(consumerRecord.topic() + ".DLT", consumerRecord.partition());
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
        handler.setAckAfterHandle(true);
        return handler;
    }
}
