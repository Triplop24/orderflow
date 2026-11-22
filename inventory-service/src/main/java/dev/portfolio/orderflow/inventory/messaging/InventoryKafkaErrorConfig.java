package dev.portfolio.orderflow.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class InventoryKafkaErrorConfig {

    public static final String ORDER_CREATED_DLT =
            "order-created.DLT";

    @Bean
    public NewTopic orderCreatedDltTopic() {
        return TopicBuilder
                .name(ORDER_CREATED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public CommonErrorHandler inventoryKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        ORDER_CREATED_DLT,
                                        record.partition()
                                )
                );

        // Do not acknowledge a failed record as recovered
        // unless Kafka confirms publishing it to the DLT.
        recoverer.setFailIfSendResultIsError(true);

        // Initial delivery + 2 retries, with 1 second
        // between attempts.
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(1000L, 2L)
                );

        // Invalid messages cannot be fixed by retrying.
        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}
