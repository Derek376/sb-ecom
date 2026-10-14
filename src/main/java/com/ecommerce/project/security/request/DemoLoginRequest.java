package com.ecommerce.project.security.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DemoLoginRequest(
        @NotBlank @Pattern(regexp = "admin|seller|customer") String role) {}
