CREATE TABLE reference_data_sync_jobs (
    job_name VARCHAR(64) NOT NULL,
    locked_until TIMESTAMP(6) NOT NULL,
    locked_by VARCHAR(64) NOT NULL,
    last_started_at TIMESTAMP(6) NOT NULL,
    last_finished_at TIMESTAMP(6) NULL,
    last_status VARCHAR(16) NOT NULL,
    last_summary VARCHAR(1000) NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_reference_data_sync_jobs PRIMARY KEY (job_name)
);
