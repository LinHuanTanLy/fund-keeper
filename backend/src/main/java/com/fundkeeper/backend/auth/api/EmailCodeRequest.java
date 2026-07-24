package com.fundkeeper.backend.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fundkeeper.backend.auth.domain.EmailCodePurpose;

public record EmailCodeRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotNull
        EmailCodePurpose purpose) {
}
