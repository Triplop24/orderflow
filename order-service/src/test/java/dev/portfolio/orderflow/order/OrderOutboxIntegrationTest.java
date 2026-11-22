package dev.portfolio.orderflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemRequest;
import dev.portfolio.orderflow.order.dto.OrderResponse;
import dev.portfolio.orderflow.order.messaging.KafkaTopicConfig;
import dev.portfolio.orderflow.order.messaging.OrderCreatedPublisher;
import dev.portfolio.orderflow.order.outbox.OutboxEvent;
import dev.portfolio.orderflow.order.outbox.OutboxEventRepository;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "orderflow.outbox.scheduling.enabled=false",
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.auto-create=false"
        }
)
@Import(OrderOutboxIntegrationTest.KafkaMockConfig.class)
@Testcontainers
class OrderOutboxIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaMockConfig {

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orders_test")
                    .withUsername("orderflow")
                    .withPassword("orderflow");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OrderCreatedPublisher outboxPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        reset(kafkaTemplate);
        outboxEventRepository.deleteAllInBatch();
    }

    @Test
    void shouldSaveOrderAndOutboxEventInSameTransaction()
            throws Exception {

        OrderResponse response =
                orderService.create(createRequest());

        assertThat(response.id()).isNotNull();

        assertThat(orderRepository.findById(response.id()))
                .isPresent();

        List<OutboxEvent> events =
                outboxEventRepository.findAll();

        assertThat(events).hasSize(1);

        OutboxEvent event = events.getFirst();

        assertThat(event.getAggregateId())
                .isEqualTo(response.id());

        assertThat(event.getEventType())
                .isEqualTo("OrderCreated");

        assertThat(event.getPublishedAt())
                .isNull();

        var payload = objectMapper.readTree(
                event.getPayload()
        );

        assertThat(payload.get("orderId").asLong())
                .isEqualTo(response.id());

        assertThat(payload.get("items").size())
                .isEqualTo(2);

        assertThat(
                payload.get("items")
                        .get(0)
                        .get("productId")
                        .asLong()
        ).isEqualTo(101L);

        assertThat(
                payload.get("items")
                        .get(0)
                        .get("quantity")
                        .asInt()
        ).isEqualTo(2);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldRollbackOrderAndOutboxTogether() {

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        Long orderId = transactionTemplate.execute(status -> {

            OrderResponse response =
                    orderService.create(createRequest());

            entityManager.flush();

            Integer orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE id = ?",
                    Integer.class,
                    response.id()
            );

            Integer outboxCount = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM outbox_events
                    WHERE aggregate_id = ?
                    """,
                    Integer.class,
                    response.id()
            );

            assertThat(orderCount).isEqualTo(1);
            assertThat(outboxCount).isEqualTo(1);

            status.setRollbackOnly();

            return response.id();
        });

        assertThat(orderId).isNotNull();

        assertThat(orderRepository.findById(orderId))
                .isEmpty();

        assertThat(outboxEventRepository.findAll())
                .isEmpty();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldRetryPublishingAfterKafkaFailure() {

        OrderResponse response =
                orderService.create(createRequest());

        OutboxEvent event =
                outboxEventRepository.findAll().getFirst();

        Long eventId = event.getId();

        CompletableFuture<SendResult<String, String>> failure =
                new CompletableFuture<>();

        failure.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        CompletableFuture<SendResult<String, String>> success =
                CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(
                eq(KafkaTopicConfig.ORDER_CREATED_TOPIC),
                eq(response.id().toString()),
                anyString()
        )).thenReturn(failure, success);

        assertThatThrownBy(
                () -> outboxPublisher.publishPending()
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Failed to publish outbox event"
                );

        OutboxEvent afterFailure =
                outboxEventRepository.findById(eventId)
                        .orElseThrow();

        assertThat(afterFailure.getPublishedAt())
                .isNull();

        outboxPublisher.publishPending();

        OutboxEvent afterSuccess =
                outboxEventRepository.findById(eventId)
                        .orElseThrow();

        assertThat(afterSuccess.getPublishedAt())
                .isNotNull();

        outboxPublisher.publishPending();

        verify(kafkaTemplate, times(2)).send(
                eq(KafkaTopicConfig.ORDER_CREATED_TOPIC),
                eq(response.id().toString()),
                anyString()
        );

        assertThat(outboxEventRepository.findAll())
                .hasSize(1);
    }

    private CreateOrderRequest createRequest() {

        return new CreateOrderRequest(
                "outbox-integration@example.com",
                List.of(
                        new OrderItemRequest(101L, 2),
                        new OrderItemRequest(205L, 1)
                )
        );
    }
}
