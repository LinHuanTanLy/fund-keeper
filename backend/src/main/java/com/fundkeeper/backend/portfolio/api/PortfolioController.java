package com.fundkeeper.backend.portfolio.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fundkeeper.backend.portfolio.application.PortfolioService;
import com.fundkeeper.backend.portfolio.application.PositionValuationService;
import com.fundkeeper.backend.portfolio.application.SellTransactionService;
import com.fundkeeper.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final SellTransactionService sellTransactionService;
    private final PositionValuationService positionValuationService;

    public PortfolioController(
            PortfolioService portfolioService,
            SellTransactionService sellTransactionService,
            PositionValuationService positionValuationService) {
        this.portfolioService = portfolioService;
        this.sellTransactionService = sellTransactionService;
        this.positionValuationService = positionValuationService;
    }

    @PostMapping("/transactions/sells")
    ResponseEntity<ApiResponse<TransactionView>> sell(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SellTransactionRequest request) {
        var outcome = sellTransactionService.sell(
                jwt.getSubject(),
                request.toCommand());
        var response = ApiResponse.success(
                TransactionView.from(outcome.details()));
        return outcome.idempotentReplay()
                ? ResponseEntity.ok(response)
                : ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response);
    }

    @PostMapping("/transactions/buys")
    ResponseEntity<ApiResponse<TransactionView>> buy(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BuyTransactionRequest request) {
        var outcome = portfolioService.buy(
                jwt.getSubject(),
                request.toCommand());
        var response = ApiResponse.success(
                TransactionView.from(outcome.details()));
        return outcome.idempotentReplay()
                ? ResponseEntity.ok(response)
                : ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response);
    }

    @GetMapping("/transactions/requests/{requestId}")
    ApiResponse<TransactionView> getByRequestId(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String requestId) {
        return ApiResponse.success(TransactionView.from(
                portfolioService.getTransactionByRequestId(
                        jwt.getSubject(),
                        requestId)));
    }

    @GetMapping("/transactions/{transactionId}")
    ApiResponse<TransactionView> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String transactionId) {
        return ApiResponse.success(TransactionView.from(
                portfolioService.getTransaction(
                        jwt.getSubject(),
                        transactionId)));
    }

    @GetMapping("/positions")
    ApiResponse<List<PositionView>> positions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String accountId) {
        var positions = portfolioService
                .listPositions(jwt.getSubject(), accountId)
                .stream()
                .map(PositionView::from)
                .toList();
        return ApiResponse.success(positions);
    }

    @GetMapping("/positions/valuations")
    ApiResponse<List<PositionValuationView>> positionValuations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String accountId) {
        var positions = positionValuationService
                .list(jwt.getSubject(), accountId)
                .stream()
                .map(PositionValuationView::from)
                .toList();
        return ApiResponse.success(positions);
    }
}
