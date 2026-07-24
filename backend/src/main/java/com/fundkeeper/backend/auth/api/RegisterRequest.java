package com.fundkeeper.backend.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        String password,

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code) {
}
