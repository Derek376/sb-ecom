package com.ecommerce.project.payload;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePaymentIntentRequest {
    @NotNull
    private Long addressId;
}
