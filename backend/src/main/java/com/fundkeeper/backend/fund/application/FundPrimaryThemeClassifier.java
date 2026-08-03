package com.fundkeeper.backend.fund.application;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fundkeeper.backend.fund.domain.FundCategory;
import com.fundkeeper.backend.fund.domain.FundDefinition;
import com.fundkeeper.backend.fund.domain.FundPrimaryTheme;

@Component
public class FundPrimaryThemeClassifier {

    public FundPrimaryTheme classify(FundDefinition fund) {
        String name = fund.name().toUpperCase(Locale.ROOT);
        if (containsAny(name, "半导体", "芯片", "集成电路")) {
            return FundPrimaryTheme.SEMICONDUCTOR;
        }
        if (containsAny(name, "互联网", "软件", "云计算", "数字经济")) {
            return FundPrimaryTheme.INTERNET;
        }
        if (containsAny(name, "消费", "食品饮料", "白酒", "家电")) {
            return FundPrimaryTheme.CONSUMER;
        }
        if (containsAny(name, "医疗", "医药", "生物")) {
            return FundPrimaryTheme.HEALTHCARE;
        }
        if (containsAny(name, "新能源", "光伏", "锂电", "电池")) {
            return FundPrimaryTheme.NEW_ENERGY;
        }
        if (containsAny(name, "金融", "银行", "证券", "保险")) {
            return FundPrimaryTheme.FINANCE;
        }
        if (containsAny(
                name,
                "QDII",
                "海外",
                "纳斯达克",
                "标普",
                "恒生")) {
            return FundPrimaryTheme.OVERSEAS;
        }
        if (containsAny(
                name,
                "沪深300",
                "中证500",
                "中证1000",
                "上证50",
                "创业板",
                "科创50")) {
            return FundPrimaryTheme.BROAD_INDEX;
        }
        if (fund.category() == FundCategory.MIXED) {
            return FundPrimaryTheme.MIXED;
        }
        return FundPrimaryTheme.OTHER;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
