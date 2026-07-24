package com.fundkeeper.backend.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fundkeeper.backend.account.domain.AccountPlatform;

public record FundAccountRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        AccountPlatform platform) {
}
