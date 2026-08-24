CREATE TABLE tags (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE task_tags (
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    tag_id  BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (task_id, tag_id)
);

CREATE INDEX idx_task_tags_tag_id ON task_tags(tag_id);
