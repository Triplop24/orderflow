package dev.portfolio.orderflow.order;

import dev.portfolio.orderflow.order.domain.OrderStatus;
import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemResponse;
import dev.portfolio.orderflow.order.dto.OrderResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    private static final String VALID_REQUEST = """
            {
              "customerEmail": "customer@example.com",
              "items": [
                {
                  "productId": 101,
                  "quantity": 2
                },
                {
                  "productId": 205,
                  "quantity": 1
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new OrderController(orderService)
                )
                .build();
    }

    @Test
    void shouldCreateOrder() throws Exception {

        when(orderService.create(any(CreateOrderRequest.class)))
                .thenReturn(createResponse());

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REQUEST)
                )
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/orders/10"
                ))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerEmail")
                        .value("customer@example.com"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId")
                        .value(101))
                .andExpect(jsonPath("$.items[0].quantity")
                        .value(2));

        verify(orderService)
                .create(any(CreateOrderRequest.class));
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {

        String invalidRequest = """
                {
                  "customerEmail": "not-an-email",
                  "items": [
                    {
                      "productId": 101,
                      "quantity": 2
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectEmptyItems() throws Exception {

        String invalidRequest = """
                {
                  "customerEmail": "customer@example.com",
                  "items": []
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectInvalidQuantity() throws Exception {

        String invalidRequest = """
                {
                  "customerEmail": "customer@example.com",
                  "items": [
                    {
                      "productId": 101,
                      "quantity": 0
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void shouldReturn400ForDuplicateProducts()
            throws Exception {

        when(orderService.create(any(CreateOrderRequest.class)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Duplicate product in order: 101"
                        )
                );

        String duplicateRequest = """
                {
                  "customerEmail": "customer@example.com",
                  "items": [
                    {
                      "productId": 101,
                      "quantity": 2
                    },
                    {
                      "productId": 101,
                      "quantity": 3
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(duplicateRequest)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetOrderById() throws Exception {

        when(orderService.getById(10L))
                .thenReturn(createResponse());

        mockMvc.perform(get("/api/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].productId")
                        .value(101));

        verify(orderService).getById(10L);
    }

    @Test
    void shouldReturn404WhenOrderNotFound()
            throws Exception {

        when(orderService.getById(999L))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: 999"
                        )
                );

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetAllOrders() throws Exception {

        when(orderService.getAll())
                .thenReturn(List.of(createResponse()));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status")
                        .value("PENDING"));

        verify(orderService).getAll();
    }

    private OrderResponse createResponse() {

        Instant now = Instant.parse(
                "2026-11-21T10:00:00Z"
        );

        return new OrderResponse(
                10L,
                "customer@example.com",
                OrderStatus.PENDING,
                List.of(
                        new OrderItemResponse(1L, 101L, 2),
                        new OrderItemResponse(2L, 205L, 1)
                ),
                now,
                now
        );
    }
}
