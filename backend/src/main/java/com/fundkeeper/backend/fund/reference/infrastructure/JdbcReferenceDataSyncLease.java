package com.fundkeeper.backend.fund.reference.infrastructure;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.reference.domain.ReferenceDataSyncLease;

@Repository
public class JdbcReferenceDataSyncLease implements ReferenceDataSyncLease {

    private static final int SUMMARY_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcReferenceDataSyncLease(
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean tryAcquire(
            String jobName,
            String ownerId,
            String description,
            Duration lockAtMostFor) {
        Instant now = clock.instant();
        Instant lockedUntil = now.plus(lockAtMostFor);
        int updated = jdbcTemplate.update(
                """
                UPDATE reference_data_sync_jobs
                   SET locked_until = ?,
                       locked_by = ?,
                       last_started_at = ?,
                       last_finished_at = NULL,
                       last_status = 'RUNNING',
                       last_summary = ?,
                       updated_at = ?
                 WHERE job_name = ?
                   AND locked_until <= ?
                """,
                Timestamp.from(lockedUntil),
                ownerId,
                Timestamp.from(now),
                limited(description),
                Timestamp.from(now),
                jobName,
                Timestamp.from(now));
        if (updated == 1) {
            return true;
        }

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO reference_data_sync_jobs
                        (job_name, locked_until, locked_by, last_started_at,
                         last_status, last_summary, updated_at)
                    VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                    """,
                    jobName,
                    Timestamp.from(lockedUntil),
                    ownerId,
                    Timestamp.from(now),
                    limited(description),
                    Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    @Transactional
    public void complete(
            String jobName,
            String ownerId,
            String status,
            String summary) {
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                UPDATE reference_data_sync_jobs
                   SET locked_until = ?,
                       last_finished_at = ?,
                       last_status = ?,
                       last_summary = ?,
                       updated_at = ?
                 WHERE job_name = ?
                   AND locked_by = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                status,
                limited(summary),
                Timestamp.from(now),
                jobName,
                ownerId);
    }

    private String limited(String value) {
        if (value == null || value.length() <= SUMMARY_LIMIT) {
            return value;
        }
        return value.substring(0, SUMMARY_LIMIT);
    }
}
