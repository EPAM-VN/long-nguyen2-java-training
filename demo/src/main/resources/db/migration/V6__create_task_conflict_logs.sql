CREATE TABLE task_conflict_logs (
    id                BIGSERIAL PRIMARY KEY,
    task_id           BIGINT      NOT NULL REFERENCES tasks(id),
    project_id        BIGINT      NOT NULL REFERENCES projects(id),
    attempted_version BIGINT      NOT NULL,
    actual_version    BIGINT      NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_conflict_logs_task_id ON task_conflict_logs(task_id);
