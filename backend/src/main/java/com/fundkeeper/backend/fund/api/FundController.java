package com.fundkeeper.backend.fund.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fundkeeper.backend.fund.application.FundCatalogService;
import com.fundkeeper.backend.shared.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/funds")
public class FundController {

    private final FundCatalogService fundCatalogService;

    public FundController(FundCatalogService fundCatalogService) {
        this.fundCatalogService = fundCatalogService;
    }

    @GetMapping("/{fundCode}")
    ApiResponse<FundView> get(@PathVariable String fundCode) {
        return ApiResponse.success(FundView.from(
                fundCatalogService.getSupportedFund(fundCode)));
    }
}
