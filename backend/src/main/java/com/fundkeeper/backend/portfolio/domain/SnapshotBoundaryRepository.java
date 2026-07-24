package com.fundkeeper.backend.portfolio.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface SnapshotBoundaryRepository {

    Optional<Instant> findLatestCommittedSnapshotAt(
            long userId,
            long accountId);

    Optional<LocalDate> findLatestPortfolioActivityDate(
            long userId,
            long accountId);
}
