CREATE TABLE tasks (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)   NOT NULL,
    priority    VARCHAR(20)   NOT NULL,
    due_date    DATE,
    assignee_id BIGINT REFERENCES users(id),
    project_id  BIGINT        NOT NULL REFERENCES projects(id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tasks_project_id ON tasks(project_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
