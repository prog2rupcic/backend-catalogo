CREATE TABLE catalog_diagnostics (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    attempt_id UUID,
    source VARCHAR(20),
    activity VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    outcome VARCHAR(20) NOT NULL DEFAULT 'UNINITIALIZED',
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    remote_version BIGINT CHECK (remote_version >= 0),
    remote_observed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(40),
    last_error_at TIMESTAMP WITH TIME ZONE
);

INSERT INTO catalog_diagnostics (id) VALUES (1);
