package com.fundkeeper.backend.fund.reference.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fundkeeper.backend.fund.reference.domain.ReferenceDataSyncLease;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcReferenceDataSyncLeaseIntegrationTests {

    @Autowired
    private ReferenceDataSyncLease lease;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyOneOwnerRunsAndCompletionPersistsStatus() {
        assertThat(lease.tryAcquire(
                "test-reference-sync",
                "instance-a",
                "DAILY_NAV:test",
                Duration.ofMinutes(10))).isTrue();
        assertThat(lease.tryAcquire(
                "test-reference-sync",
                "instance-b",
                "DAILY_NAV:test",
                Duration.ofMinutes(10))).isFalse();

        lease.complete(
                "test-reference-sync",
                "instance-a",
                "SUCCESS",
                "navs=1");

        assertThat(jdbcTemplate.queryForMap(
                        """
                        SELECT last_status, last_summary, last_finished_at
                          FROM reference_data_sync_jobs
                         WHERE job_name = 'test-reference-sync'
                        """))
                .containsEntry("LAST_STATUS", "SUCCESS")
                .containsEntry("LAST_SUMMARY", "navs=1")
                .containsKey("LAST_FINISHED_AT");

        assertThat(lease.tryAcquire(
                "test-reference-sync",
                "instance-b",
                "FULL:test",
                Duration.ofMinutes(10))).isTrue();
    }
}
