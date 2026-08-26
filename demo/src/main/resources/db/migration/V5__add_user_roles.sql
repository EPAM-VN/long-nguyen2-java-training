CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id),
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);
