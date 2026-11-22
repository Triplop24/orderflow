package dev.portfolio.orderflow.order.messaging;

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
public class OrderKafkaErrorConfig {

    public static final String INVENTORY_RESULT_DLT =
            "inventory-result.DLT";

    @Bean
    public NewTopic inventoryResultDltTopic() {
        return TopicBuilder
                .name(INVENTORY_RESULT_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public CommonErrorHandler orderKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate
    ) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        INVENTORY_RESULT_DLT,
                                        record.partition()
                                )
                );

        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        new FixedBackOff(1000L, 2L)
                );

        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }
}
