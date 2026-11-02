CREATE TABLE professional_category (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE professional (
    id BIGINT PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES professional_category(id),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_professional_category ON professional(category_id);

CREATE TABLE weekly_schedule (
    id BIGINT PRIMARY KEY,
    professional_id BIGINT NOT NULL REFERENCES professional(id),
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL CHECK (slot_duration_minutes > 0),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_schedule_time CHECK (start_time < end_time)
);
CREATE INDEX idx_schedule_professional ON weekly_schedule(professional_id);

CREATE TABLE catalog_sync_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    applied_version BIGINT NOT NULL CHECK (applied_version >= 0),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
