package com.fundkeeper.backend.account.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fundkeeper.backend.account.application.FundAccountService;
import com.fundkeeper.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/accounts")
public class FundAccountController {

    private final FundAccountService accountService;

    public FundAccountController(FundAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    ApiResponse<List<FundAccountView>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        var accounts = accountService.list(jwt.getSubject(), includeArchived)
                .stream()
                .map(FundAccountView::from)
                .toList();
        return ApiResponse.success(accounts);
    }

    @GetMapping("/{accountId}")
    ApiResponse<FundAccountView> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId) {
        return ApiResponse.success(FundAccountView.from(
                accountService.get(jwt.getSubject(), accountId)));
    }

    @PostMapping
    ResponseEntity<ApiResponse<FundAccountView>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody FundAccountRequest request) {
        var account = accountService.create(
                jwt.getSubject(),
                request.name(),
                request.platform());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(FundAccountView.from(account)));
    }

    @PutMapping("/{accountId}")
    ApiResponse<FundAccountView> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId,
            @Valid @RequestBody FundAccountRequest request) {
        return ApiResponse.success(FundAccountView.from(
                accountService.update(
                        jwt.getSubject(),
                        accountId,
                        request.name(),
                        request.platform())));
    }

    @PostMapping("/{accountId}/archive")
    ApiResponse<FundAccountView> archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId) {
        return ApiResponse.success(FundAccountView.from(
                accountService.archive(jwt.getSubject(), accountId)));
    }
}
