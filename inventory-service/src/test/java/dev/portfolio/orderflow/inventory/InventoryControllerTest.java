package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.dto.InventoryReservationResponse;
import dev.portfolio.orderflow.inventory.dto.ProductResponse;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    private MockMvc mockMvc;

    private static final String VALID_RESERVATION = """
            {
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
                        new ProductController(inventoryService),
                        new InventoryController(inventoryService)
                )
                .build();
    }

    @Test
    void shouldReturnAllProducts() throws Exception {
        when(inventoryService.getAllProducts())
                .thenReturn(List.of(createProductResponse()));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].sku").value("LAPTOP-001"));
    }

    @Test
    void shouldReturnProductById() throws Exception {
        when(inventoryService.getProductById(101L))
                .thenReturn(createProductResponse());

        mockMvc.perform(get("/api/products/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.availableQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0));
    }

    @Test
    void shouldReturn404ForMissingProduct() throws Exception {
        when(inventoryService.getProductById(999L))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found: 999"
                        )
                );

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReserveInventory() throws Exception {
        when(inventoryService.reserve(any(ReserveInventoryRequest.class)))
                .thenReturn(
                        new InventoryReservationResponse(
                                "RESERVED",
                                List.of(
                                        new InventoryReservationResponse.Item(
                                                101L, 2, 8, 2
                                        ),
                                        new InventoryReservationResponse.Item(
                                                205L, 1, 49, 1
                                        )
                                )
                        )
                );

        mockMvc.perform(
                        post("/api/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_RESERVATION)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(101))
                .andExpect(jsonPath("$.items[0].reservedQuantity").value(2))
                .andExpect(jsonPath("$.items[0].remainingAvailable").value(8));

        verify(inventoryService)
                .reserve(any(ReserveInventoryRequest.class));
    }

    @Test
    void shouldRejectEmptyItems() throws Exception {
        mockMvc.perform(
                        post("/api/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "items": []
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    @Test
    void shouldRejectInvalidQuantity() throws Exception {
        mockMvc.perform(
                        post("/api/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "items": [
                                            {
                                              "productId": 101,
                                              "quantity": 0
                                            }
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    @Test
    void shouldReturn400ForDuplicateProducts() throws Exception {
        when(inventoryService.reserve(any(ReserveInventoryRequest.class)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Duplicate product: 101"
                        )
                );

        mockMvc.perform(
                        post("/api/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
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
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409ForInsufficientStock() throws Exception {
        when(inventoryService.reserve(any(ReserveInventoryRequest.class)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Insufficient stock for product: 101"
                        )
                );

        mockMvc.perform(
                        post("/api/inventory/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_RESERVATION)
                )
                .andExpect(status().isConflict());
    }

    private ProductResponse createProductResponse() {
        return new ProductResponse(
                101L,
                "LAPTOP-001",
                "Laptop",
                10,
                0
        );
    }
}
