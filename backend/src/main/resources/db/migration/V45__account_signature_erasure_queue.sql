-- Durable file-erasure queue: only newly requested deletions are affected.
-- No bulk deletion of existing users, tenants or business records.
CREATE TABLE signature_erasure_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    file_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
