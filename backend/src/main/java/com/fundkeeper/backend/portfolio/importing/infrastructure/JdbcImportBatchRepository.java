package com.fundkeeper.backend.portfolio.importing.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fundkeeper.backend.portfolio.domain.SnapshotBoundaryRepository;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatch;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchRepository;
import com.fundkeeper.backend.portfolio.importing.domain.ImportBatchStatus;

@Repository
public class JdbcImportBatchRepository
        implements ImportBatchRepository, SnapshotBoundaryRepository {

    private static final String COLUMNS = """
            SELECT id, public_id, user_id, account_id, batch_id,
                   schema_version, import_type, snapshot_mode, snapshot_at,
                   content_hash, plan_hash, request_json, status,
                   total_count, importable_count, warning_count, error_count,
                   preflight_json, commit_result_json, committed_at,
                   created_at, updated_at
              FROM portfolio_import_batches
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcImportBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ImportBatch> findByUserIdAndBatchId(
            long userId,
            String batchId) {
        return first(jdbcTemplate.query(
                COLUMNS + " WHERE user_id = ? AND batch_id = ?",
                this::map,
                userId,
                batchId));
    }

    @Override
    public Optional<ImportBatch> findByUserIdAndBatchIdForUpdate(
            long userId,
            String batchId) {
        return first(jdbcTemplate.query(
                COLUMNS
                        + " WHERE user_id = ? AND batch_id = ? FOR UPDATE",
                this::map,
                userId,
                batchId));
    }

    @Override
    public ImportBatch savePreflight(ImportBatch batch) {
        if (batch.id() == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO portfolio_import_batches
                        (public_id, user_id, account_id, batch_id,
                         schema_version, import_type, snapshot_mode,
                         snapshot_at, content_hash, plan_hash, request_json,
                         status, total_count, importable_count, warning_count,
                         error_count, preflight_json, commit_result_json,
                         committed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                            ?, NULL, NULL, ?, ?, 0)
                    """,
                    batch.publicId(),
                    batch.userId(),
                    batch.accountId(),
                    batch.batchId(),
                    batch.schemaVersion(),
                    batch.importType(),
                    batch.snapshotMode(),
                    timestamp(batch.snapshotAt()),
                    batch.contentHash(),
                    batch.planHash(),
                    batch.requestJson(),
                    batch.status().name(),
                    batch.totalCount(),
                    batch.importableCount(),
                    batch.warningCount(),
                    batch.errorCount(),
                    batch.preflightJson(),
                    Timestamp.from(batch.createdAt()),
                    Timestamp.from(batch.updatedAt()));
        } else {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE portfolio_import_batches
                       SET account_id = ?,
                           schema_version = ?,
                           import_type = ?,
                           snapshot_mode = ?,
                           snapshot_at = ?,
                           content_hash = ?,
                           plan_hash = ?,
                           request_json = ?,
                           status = ?,
                           total_count = ?,
                           importable_count = ?,
                           warning_count = ?,
                           error_count = ?,
                           preflight_json = ?,
                           commit_result_json = NULL,
                           committed_at = NULL,
                           updated_at = ?,
                           version = version + 1
                     WHERE id = ?
                       AND status <> 'COMMITTED'
                    """,
                    batch.accountId(),
                    batch.schemaVersion(),
                    batch.importType(),
                    batch.snapshotMode(),
                    timestamp(batch.snapshotAt()),
                    batch.contentHash(),
                    batch.planHash(),
                    batch.requestJson(),
                    batch.status().name(),
                    batch.totalCount(),
                    batch.importableCount(),
                    batch.warningCount(),
                    batch.errorCount(),
                    batch.preflightJson(),
                    Timestamp.from(batch.updatedAt()),
                    batch.id());
            if (updated != 1) {
                throw new IllegalStateException(
                        "Committed import batch cannot be replaced");
            }
        }
        return findByUserIdAndBatchId(batch.userId(), batch.batchId())
                .orElseThrow(() -> new IllegalStateException(
                        "Saved import batch cannot be read"));
    }

    @Override
    public ImportBatch markCommitted(
            long id,
            long accountId,
            String commitResultJson,
            Instant committedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE portfolio_import_batches
                   SET account_id = ?,
                       status = 'COMMITTED',
                       commit_result_json = ?,
                       committed_at = ?,
                       updated_at = ?,
                       version = version + 1
                 WHERE id = ?
                   AND status = 'READY_TO_COMMIT'
                """,
                accountId,
                commitResultJson,
                Timestamp.from(committedAt),
                Timestamp.from(committedAt),
                id);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Import batch is no longer ready to commit");
        }
        return first(jdbcTemplate.query(
                COLUMNS + " WHERE id = ?",
                this::map,
                id)).orElseThrow(() -> new IllegalStateException(
                        "Committed import batch cannot be read"));
    }

    @Override
    public void markCommitFailed(
            long userId,
            String batchId,
            Instant failedAt) {
        jdbcTemplate.update(
                """
                UPDATE portfolio_import_batches
                   SET status = 'COMMIT_FAILED',
                       updated_at = ?,
                       version = version + 1
                 WHERE user_id = ?
                   AND batch_id = ?
                   AND status = 'READY_TO_COMMIT'
                """,
                Timestamp.from(failedAt),
                userId,
                batchId);
    }

    @Override
    public Optional<Instant> findLatestCommittedSnapshotAt(
            long userId,
            long accountId) {
        return first(jdbcTemplate.query(
                """
                SELECT snapshot_at
                  FROM portfolio_import_batches
                 WHERE user_id = ?
                   AND account_id = ?
                   AND import_type = 'POSITION_SNAPSHOT'
                   AND status = 'COMMITTED'
                 ORDER BY snapshot_at DESC
                 LIMIT 1
                """,
                (resultSet, rowNumber) ->
                        resultSet.getTimestamp("snapshot_at").toInstant(),
                userId,
                accountId));
    }

    @Override
    public Optional<LocalDate> findLatestPortfolioActivityDate(
            long userId,
            long accountId) {
        return first(jdbcTemplate.query(
                """
                SELECT effective_trade_date AS latest_date
                  FROM fund_transactions
                 WHERE user_id = ?
                   AND account_id = ?
                   AND status NOT IN ('CANCELLED', 'REVERSED')
                 ORDER BY effective_trade_date DESC
                 LIMIT 1
                """,
                (resultSet, rowNumber) ->
                        resultSet.getObject(
                                "latest_date",
                                LocalDate.class),
                userId,
                accountId));
    }

    private ImportBatch map(
            ResultSet resultSet,
            int rowNumber) throws SQLException {
        return new ImportBatch(
                resultSet.getLong("id"),
                resultSet.getString("public_id"),
                resultSet.getLong("user_id"),
                (Long) resultSet.getObject("account_id"),
                resultSet.getString("batch_id"),
                resultSet.getString("schema_version"),
                resultSet.getString("import_type"),
                resultSet.getString("snapshot_mode"),
                instant(resultSet, "snapshot_at"),
                resultSet.getString("content_hash"),
                resultSet.getString("plan_hash"),
                resultSet.getString("request_json"),
                ImportBatchStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("total_count"),
                resultSet.getInt("importable_count"),
                resultSet.getInt("warning_count"),
                resultSet.getInt("error_count"),
                resultSet.getString("preflight_json"),
                resultSet.getString("commit_result_json"),
                instant(resultSet, "committed_at"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private Instant instant(
            ResultSet resultSet,
            String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
