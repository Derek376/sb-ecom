package com.ecommerce.project.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDTO {
    @Schema(description = "Unique identifier of the category", example = "1")
    private Long categoryId;

    @Schema(description = "Name of the category", example = "Electronics")
    private String categoryName;
}
