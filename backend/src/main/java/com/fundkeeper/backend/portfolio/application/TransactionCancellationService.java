package com.fundkeeper.backend.portfolio.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.portfolio.domain.TransactionType;
import com.fundkeeper.backend.shared.exception.BusinessException;
import com.fundkeeper.backend.shared.exception.ErrorCode;

@Service
public class TransactionCancellationService {

    private final PortfolioService portfolioService;
    private final BuyTransactionStateService buyTransactionStateService;
    private final SellTransactionService sellTransactionService;

    public TransactionCancellationService(
            PortfolioService portfolioService,
            BuyTransactionStateService buyTransactionStateService,
            SellTransactionService sellTransactionService) {
        this.portfolioService = portfolioService;
        this.buyTransactionStateService = buyTransactionStateService;
        this.sellTransactionService = sellTransactionService;
    }

    @Transactional
    public TransactionDetails cancel(
            String userPublicId,
            String transactionPublicId,
            String reason) {
        TransactionType type = portfolioService
                .getTransaction(
                        userPublicId,
                        transactionPublicId)
                .transaction()
                .type();
        return switch (type) {
            case BUY -> buyTransactionStateService.cancel(
                    userPublicId,
                    transactionPublicId,
                    reason);
            case SELL -> sellTransactionService.cancel(
                    userPublicId,
                    transactionPublicId,
                    new SellCancellationCommand(reason));
            default -> throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "当前交易类型不支持撤销");
        };
    }
}
