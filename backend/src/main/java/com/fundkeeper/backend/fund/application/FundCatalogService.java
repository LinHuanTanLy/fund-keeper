package com.fundkeeper.backend.fund.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class FundCatalogService {

    private final FundDataRepository fundDataRepository;

    public FundCatalogService(FundDataRepository fundDataRepository) {
        this.fundDataRepository = fundDataRepository;
    }

    @Transactional(readOnly = true)
    public FundDefinition getSupportedFund(String rawCode) {
        String code = normalizeCode(rawCode);
        FundDefinition fund = fundDataRepository.findFundByCode(code)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.FUND_NOT_FOUND,
                        "基金代码不存在"));
        if (!fund.supported() || !"CNY".equals(fund.currency())) {
            throw new BusinessException(
                    ErrorCode.FUND_NOT_SUPPORTED,
                    "该基金不在 V1 支持范围内");
        }
        return fund;
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BusinessException(
                    ErrorCode.FUND_NOT_FOUND,
                    "基金代码不存在");
        }
        return code;
    }
}
