package com.fundkeeper.backend.fund.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.FundPrimaryTheme;

class FundPrimaryThemeClassifierTests {

    private final FundPrimaryThemeClassifier classifier =
            new FundPrimaryThemeClassifier();

    @Test
    void classifiesOnlyExplicitThemeSignalsAndFallsBackSafely() {
        assertThat(classify(
                "华夏国证半导体芯片ETF联接C",
                FundCategory.INDEX))
                .isEqualTo(FundPrimaryTheme.SEMICONDUCTOR);
        assertThat(classify(
                "招商移动互联网产业股票基金C",
                FundCategory.STOCK))
                .isEqualTo(FundPrimaryTheme.INTERNET);
        assertThat(classify(
                "沪深300指数增强",
                FundCategory.INDEX))
                .isEqualTo(FundPrimaryTheme.BROAD_INDEX);
        assertThat(classify(
                "稳健成长混合",
                FundCategory.MIXED))
                .isEqualTo(FundPrimaryTheme.MIXED);
        assertThat(classify(
                "行业精选股票",
                FundCategory.STOCK))
                .isEqualTo(FundPrimaryTheme.OTHER);
    }

    private FundPrimaryTheme classify(
            String name,
            FundCategory category) {
        Instant now = Instant.parse("2026-07-27T00:00:00Z");
        return classifier.classify(new FundDefinition(
                1L,
                "000001",
                name,
                category,
                "CNY",
                true,
                1,
                "test",
                now,
                now));
    }
}
