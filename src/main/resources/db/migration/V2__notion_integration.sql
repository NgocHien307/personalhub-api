CREATE TABLE notion_tasks (
    id UUID PRIMARY KEY,
    data_source_id UUID NOT NULL,
    title TEXT NOT NULL,
    status TEXT,
    due_start TEXT,
    due_end TEXT,
    due_time_zone TEXT,
    url TEXT,
    last_edited_at TIMESTAMPTZ NOT NULL,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    synced_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notion_tasks_source_edited ON notion_tasks(data_source_id, last_edited_at DESC, id);
CREATE TABLE notion_sync_states (
    data_source_id UUID PRIMARY KEY,
    last_success_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE notion_sync_runs (
    id UUID PRIMARY KEY,
    data_source_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'CONFLICT')),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    tasks_processed INT NOT NULL DEFAULT 0,
    error_message TEXT
);
CREATE INDEX idx_notion_runs_source_started ON notion_sync_runs(data_source_id, started_at DESC);
