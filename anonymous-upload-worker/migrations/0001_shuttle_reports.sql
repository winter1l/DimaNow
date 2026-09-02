CREATE TABLE IF NOT EXISTS shuttle_reports (
    service_date TEXT NOT NULL,
    schedule_revision INTEGER NOT NULL,
    run_id TEXT NOT NULL,
    stop_call_id TEXT NOT NULL,
    stop_sequence INTEGER NOT NULL,
    reporter_hash TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (service_date, schedule_revision, run_id, stop_call_id, reporter_hash)
);

CREATE INDEX IF NOT EXISTS shuttle_reports_lookup
    ON shuttle_reports (service_date, schedule_revision, run_id, stop_sequence);
