package com.fundkeeper.backend.portfolio.application;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fundkeeper.backend.fund.domain.FundDataRepository;
import com.fundkeeper.backend.portfolio.domain.SubmittedPeriod;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class TradingCalendarService {

    private final FundDataRepository fundDataRepository;

    public TradingCalendarService(FundDataRepository fundDataRepository) {
        this.fundDataRepository = fundDataRepository;
    }

    public LocalDate effectiveTradeDate(
            LocalDate submittedDate,
            SubmittedPeriod submittedPeriod) {
        LocalDate firstCandidate =
                submittedPeriod == SubmittedPeriod.BEFORE_15
                        ? submittedDate
                        : submittedDate.plusDays(1);
        return nextOpenDate(firstCandidate);
    }

    public Optional<LocalDate> estimatedConfirmationDate(
            LocalDate effectiveTradeDate,
            Integer delayTradingDays) {
        if (delayTradingDays == null) {
            return Optional.empty();
        }
        if (delayTradingDays < 0) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "基金确认规则数据不正确");
        }
        LocalDate result = effectiveTradeDate;
        for (int index = 0; index < delayTradingDays; index++) {
            result = nextOpenDate(result.plusDays(1));
        }
        return Optional.of(result);
    }

    private LocalDate nextOpenDate(LocalDate firstCandidate) {
        LocalDate candidate = firstCandidate;
        for (int checkedDays = 0; checkedDays < 370; checkedDays++) {
            boolean open = fundDataRepository
                    .findTradingDayOpenFlag(candidate)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.TRADING_CALENDAR_UNAVAILABLE,
                            "交易日历暂不可用，请稍后重试"));
            if (open) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new BusinessException(
                ErrorCode.TRADING_CALENDAR_UNAVAILABLE,
                "交易日历暂不可用，请稍后重试");
    }
}
