package com.ecommerce.project.payload;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private Long productId;

    @NotBlank
    @Size(min = 3, max = 120)
    private String productName;
    private String image;

    @NotBlank
    @Size(min = 6, max = 2000)
    private String description;

    @NotNull
    @PositiveOrZero
    private Integer quantity;

    @NotNull
    @Positive
    private Double price;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double discount;
    private Double specialPrice;
}
