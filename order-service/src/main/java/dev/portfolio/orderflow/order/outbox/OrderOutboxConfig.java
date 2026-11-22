package dev.portfolio.orderflow.order.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "orderflow.outbox.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderOutboxConfig {
}
