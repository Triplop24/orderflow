package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.dto.InventoryReservationResponse;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reservations")
    public InventoryReservationResponse reserve(
            @Valid @RequestBody ReserveInventoryRequest request
    ) {
        return inventoryService.reserve(request);
    }
}
