package dev.portfolio.orderflow.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReserveInventoryRequest(

        @NotEmpty
        @Size(max = 50)
        List<@Valid Item> items

) {

    public record Item(

            @NotNull
            @Positive
            Long productId,

            @NotNull
            @Positive
            Integer quantity

    ) {
    }
}
