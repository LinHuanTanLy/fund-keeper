package com.fundkeeper.backend.portfolio.importing.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fundkeeper.backend.portfolio.importing.application.TransactionBatchCommitResult;
import com.fundkeeper.backend.portfolio.importing.application.TransactionBatchImportService;
import com.fundkeeper.backend.portfolio.importing.application.TransactionBatchPreflightResult;
import com.fundkeeper.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/imports/transaction-batches")
public class TransactionBatchImportController {

    private final TransactionBatchImportService importService;

    public TransactionBatchImportController(
            TransactionBatchImportService importService) {
        this.importService = importService;
    }

    @PostMapping(
            value = "/preflight",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<TransactionBatchPreflightResult> preflight(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody String rawJson) {
        return ApiResponse.success(
                importService.preflight(
                        jwt.getSubject(),
                        rawJson));
    }

    @PostMapping("/{batchId}/commit")
    ResponseEntity<ApiResponse<TransactionBatchCommitResult>> commit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String batchId) {
        var outcome = importService.commit(
                jwt.getSubject(),
                batchId);
        var body = ApiResponse.success(outcome.result());
        return outcome.idempotentReplay()
                ? ResponseEntity.ok(body)
                : ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(body);
    }
}
