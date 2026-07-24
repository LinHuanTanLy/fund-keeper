package com.fundkeeper.backend.portfolio.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataFundPositionRepository
        extends JpaRepository<FundPositionJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FundPositionJpaEntity> findByAccountIdAndFundId(
            long accountId,
            long fundId);

    List<FundPositionJpaEntity> findAllByUserIdOrderByCreatedAtAsc(
            long userId);

    List<FundPositionJpaEntity> findAllByUserIdAndAccountIdOrderByCreatedAtAsc(
            long userId,
            long accountId);
}
